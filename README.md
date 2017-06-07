## An auction website project written in Scala using Play, Akka, Websockets, NoSQL

### Auction FSM required tests

#### Auction
    
| ReservePrice? | Renewal? | Bidders | Test file            | Should be                                                                      |
|---------------|----------|---------|----------------------|--------------------------------------------------------------------------------|
| Yes           | No       | No      | AuctionActorSpec     | Closed when end-time is reached, without winner                                |
| Yes           | Yes      | No      | AuctionActorSpec     | Restarted when end-time is reached, without winner                             |
| Yes           | -        | A       | AuctionActorSpec     | Closed when end-time is reached, winner A for his single bid                   |
| Yes           | -        | A,B     | AuctionActorSpec     | Closed when end-time is reached, winner B                                      |
| No            | No       | A       | AuctionActorSpec     | Closed when end-time is reached, without winner (highest bid < reserve price   |
| No            | No       | A       | AuctionActorSpec     | Closed when end-time is reached, winner A (bid = reserve price)                |
| No            | No       | A       | AuctionActorSpec     | Closed when end-time is reached, winner A (bid > reserve price)                |
|               |          |         |                      |                                                                                |

#### Fixed price

| Renewal? | Bidders | Test file                  | Should be                                                                      |
|----------|---------|----------------------------|--------------------------------------------------------------------------------|
| No       | No      | FixedPriceAuctionActorSpec | Closed and not sold                                                            |
| Yes      | No      | FixedPriceAuctionActorSpec | Restarted and not sold                                                         |
| -        | A       | FixedPriceAuctionActorSpec | Closed and sold for the total stock available                                  |
| -        | A       | FixedPriceAuctionActorSpec | Closed and sold for the requested qty, CLONED for the remaining stock          |
