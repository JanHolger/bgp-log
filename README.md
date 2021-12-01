# bgp-log
Tool to log all prefixes and their as path of a BGP 4 session to MongoDB. It's written in pure Java using the [bgp-java](https://github.com/LUMASERV/bgp-java) bgp implementation.

## Collections
sessions
```json
{
  "_id": "02cdd525d77148c0ae114973",
  "peer": "BGP_PEER_NAME",
  "opened_at": "2021-11-30T20:00:00.000Z",
  "closed_at": "2021-11-30T21:00:00.000Z"
}
```
routes
```json
{
  "_id": "50669d8e007bbfb4c2fd193e",
  "session_id": "02cdd525d77148c0ae114973",
  "address": 2130706433,
  "length": 24,
  "plain": "127.0.0.1/24",
  "as_path": [
    13335,
    32943,
    15169
  ],
  "received_at": "2021-11-30T20:00:00.000Z",
  "withdrawn_at": "2021-11-30T20:30:00.000Z"
}
```

## Configuration
Configuration can be done through environment variables or a `.env` file.

Name                | Description
------------------- | -------------------------
MONGODB_URL         | The MongoDB connection url
MONGODB_DATABASE    | MongoDB database name
BGP_PORT            | BGP Port to listen on (default: 179)
BGP_HIDE_AS         | Comma seperated list of as numbers which should be hidden from the as path
BGP_LOCAL_AS        | Local AS Number
BGP_LOCAL_ROUTER_ID | Local Router ID (usually the own ip)
BGP_PEER_NAME       | An identifier for this session in the database
BGP_PEER_AS         | AS Number of the remote router
BGP_PEER_ROUTER_ID  | Router ID of the remote router (this could be the ip of the neighbor but it's unlikely)

## Example Query
This MongoDB Shell command returns all active prefixes announced by Google
```js
db.routes.find({
    $expr: {
        $eq: [ { $last: "$as_path" }, 15169 ]
    },
    withdrawn_at: {
        $exists: false
    }
}, {
    plain: 1,
    as_path: 1
})
```
