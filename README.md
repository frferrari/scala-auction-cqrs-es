# An auction website project written in Scala using Play, Akka, Websockets, NoSQL

### Auction FSM required tests

#### Auction

| ReservePrice? | Renewal? | Bidders | Test file            | Should be                                                      |
|---------------|----------|---------|----------------------|----------------------------------------------------------------|
| W/O           | W/O      | W/O     | AuctionActorSpec1    | Closed when end-time is reached, without winner                |
| W/O           | W        | W/O     | AuctionActorSpec2    | Restarted when end-time is reached, without winner             |
| W/O           | -        | A       | AuctionActorSpec3    | Closed when end-time is reached, winner A for his single bid   |
| W/O           | -        | A,B     | AuctionActorSpec4    | Closed when end-time is reached, winner B                      |
|               |          |         |                      |                                                                |

#### Fixed price