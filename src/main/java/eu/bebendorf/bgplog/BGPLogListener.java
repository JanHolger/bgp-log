package eu.bebendorf.bgplog;

import com.lumaserv.bgp.BGPListener;
import com.lumaserv.bgp.BGPSession;
import com.lumaserv.bgp.protocol.attribute.AS4PathAttribute;
import com.lumaserv.bgp.protocol.attribute.ASPathAttribute;
import com.lumaserv.bgp.protocol.attribute.PathAttribute;
import com.lumaserv.bgp.protocol.message.BGPUpdate;
import lombok.AllArgsConstructor;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
public class BGPLogListener implements BGPListener {

    BGPLog bgpLog;

    public void onOpen(BGPSession bgpSession) {
        new HashSet<>(bgpLog.getSessionIds().entrySet()).stream().filter(s -> s.getKey().getConfiguration().getName().equals(bgpSession.getConfiguration().getName())).forEach(e -> bgpLog.sessionEnded(null, e.getKey()));
        Document document = new Document()
                .append("peer", bgpSession.getConfiguration().getName())
                .append("opened_at", Date.from(Instant.now()));
        bgpLog.getSessions().insertOne(document);
        String id = document.getObjectId("_id").toString();
        bgpLog.getSessionIds().put(bgpSession, id);
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
        bgpLog.sessionEnded(null, bgpSession);
    }

}
