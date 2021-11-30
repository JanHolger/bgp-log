package eu.bebendorf.bgplog;

import com.lumaserv.bgp.BGPListener;
import com.lumaserv.bgp.BGPSession;
import com.lumaserv.bgp.protocol.attribute.AS4PathAttribute;
import com.lumaserv.bgp.protocol.attribute.ASPathAttribute;
import com.lumaserv.bgp.protocol.attribute.PathAttribute;
import com.lumaserv.bgp.protocol.message.BGPUpdate;
import lombok.AllArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class BGPLogListener implements BGPListener {

    BGPLog bgpLog;

    public void onOpen(BGPSession bgpSession) {
        Document document = new Document()
                .append("peer", bgpSession.getConfiguration().getName())
                .append("opened_at", Date.from(Instant.now()));
        bgpLog.getSessions().insertOne(document);
        String id = document.getObjectId("_id").toString();
        bgpLog.getSessionIds().put(bgpSession, id);
        System.out.println("Session " + id + " with peer " + bgpSession.getConfiguration().getName() + " establied!");
    }

    public void onUpdate(BGPSession bgpSession, BGPUpdate bgpUpdate) {
        String sessionId = bgpLog.getSessionIds().get(bgpSession);
        if(sessionId == null)
            return;
        Date date = Date.from(Instant.now());
        bgpUpdate.getWithdrawnPrefixes().forEach(ipPrefix -> {
            bgpLog.getRoutes().updateMany(new Document()
                            .append("session_id", sessionId)
                            .append("address", AddressHelper.addressToLong(ipPrefix.getAddress()))
                            .append("length", (int) ipPrefix.getLength())
                            .append("withdrawn_at", null),
                    new Document()
                            .append("$set", new Document()
                                    .append("withdrawn_at", date)
                            )
            );
        });
        bgpUpdate.getPrefixes().forEach(ipPrefix -> {
            PathAttribute attribute = bgpUpdate.getAttributes().stream().filter(a -> (a instanceof ASPathAttribute) || (a instanceof AS4PathAttribute)).findFirst().orElse(null);
            if(attribute == null)
                return;

            List<Integer> asns = new ArrayList<>();
            if(attribute instanceof ASPathAttribute) {
                ((ASPathAttribute) attribute).getSegments().forEach(s -> asns.addAll(s.getAsns()));
            } else {
                ((AS4PathAttribute) attribute).getSegments().forEach(s -> asns.addAll(s.getAsns()));
            }
            bgpLog.getRoutes().insertOne(new Document()
                    .append("received_at", date)
                    .append("session_id", sessionId)
                    .append("address", AddressHelper.addressToLong(ipPrefix.getAddress()))
                    .append("length", (int) ipPrefix.getLength())
                    .append("plain", AddressHelper.addressToString(ipPrefix.getAddress()) + "/" + ipPrefix.getLength())
                    .append("as_path", asns.stream().filter(asn -> !bgpLog.getHiddenAsns().contains(asn)).collect(Collectors.toList()))
            );
        });
    }

    public void onClose(BGPSession bgpSession) {
        String sessionId = bgpLog.getSessionIds().get(bgpSession);
        if(sessionId == null)
            return;
        Date date = Date.from(Instant.now());
        bgpLog.getRoutes().updateMany(new Document()
                        .append("session_id", sessionId)
                        .append("withdrawn_at", null),
                new Document()
                        .append("$set", new Document()
                                .append("withdrawn_at", date)
                        )
        );
        bgpLog.getSessions().updateOne(new Document()
                        .append("id", new ObjectId(sessionId)),
                new Document()
                        .append("$set", new Document()
                                .append("closed_at", date)
                        )
        );
        bgpLog.getSessionIds().remove(bgpSession);
        System.out.println("Session " + sessionId + " with peer " + bgpSession.getConfiguration().getName() + " gracefully closed!");
    }

}
