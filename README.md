## An auction website project written in Scala using Play, Akka, Websockets, NoSQL

### Auction FSM required tests

#### Auction
    
| ReservePrice? | Renewal? | Bidders | Test file            | Should be                                                                      |
|---------------|----------|---------|----------------------|--------------------------------------------------------------------------------|
| Yes           | No       | No      | AuctionActorSpec1    | Closed when end-time is reached, without winner                                |
| Yes           | Yes      | No      | AuctionActorSpec2    | Restarted when end-time is reached, without winner                             |
| Yes           | -        | A       | AuctionActorSpec3    | Closed when end-time is reached, winner A for his single bid                   |
| Yes           | -        | A,B     | AuctionActorSpec4    | Closed when end-time is reached, winner B                                      |
| No            | No       | A       | AuctionActorSpec5    | Closed when end-time is reached, without winner (highest bid < reserve price   |
| No            | No       | A       | AuctionActorSpec6    | Closed when end-time is reached, winner A (bid = reserve price)                |
| No            | No       | A       | AuctionActorSpec7    | Closed when end-time is reached, winner A (bid > reserve price)                |
|               |          |         |                      |                                                                                |

#### Fixed price

| Renewal? | Bidders | Test file                      | Should be                                                                      |
|----------|---------|--------------------------------|--------------------------------------------------------------------------------|
| No       | No      | FixedPriceAuctionActorSpec1    | Closed and not sold                                                            |
| Yes      | No      | FixedPriceAuctionActorSpec2    | Restarted and not sold                                                         |
| -        | A       | FixedPriceAuctionActorSpec3    | Closed and sold for the total stock available                                  |
| -        | A       | FixedPriceAuctionActorSpec4    | Closed and sold for the requested qty, cloned for the remaining stock          |
