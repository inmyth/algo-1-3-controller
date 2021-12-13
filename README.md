# Algo 1-3 Controller


## Development Notes
### Unable to compile
- Check that all files have package name
Files in `/script` should have project name `mrt` as package name (will cause error warning on Intellij)
- Some cats errors do not propagate. 
This happens when EitherT composition is missing type. I solved this by reducing the composition to single lines and check the compile result on Horizon Script Manager.


### Behavior
- check isDefined and 0 in params that have it
- check gap between market open / close signal between dw and ul
- bot should stop when order executed signal (from ul) arrives. this means the market has changed status. bot stops when it gets any ul executed signal.  
- dw and ul can open or close at different time. 
- in case of runtime error Horizon will stop sending signals
- a market order from Horizon can be missing one leg, this causes the bot to cancel an order
- ScenarioStatus can be null, its fields like priceOnMarket and qtyOnMarket can be null
```
marketSells Vector(MyScenarioStatus(0.18,35400), MyScenarioStatus(0.19,1056500), MyScenarioStatus(0.2,1032500), MyScenarioStatus(0.21,1020900), MyScenarioStatus(0.22,1042400))
marketSells Vector(MyScenarioStatus(0.19,1056500), MyScenarioStatus(0.2,1032500), MyScenarioStatus(0.21,1020900), MyScenarioStatus(0.22,1042400))
```
- [] stop the bot when dw opens before ul (executed signal)

## Creating new Gradle project
1. Horizon > create new strategy > Generate IDEA files 
2. Go to Idea > Open Gradle Tab > Click Refresh

### Give Package Name !


    Line 98598: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 1. Dw List: List(DW(BBL24C2201A@XBKK,Some(0.07),Some(55100),Some(0.009823354829507138),Some(CALL),Vector(MyScenarioStatus(0.07,55100), MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300)),Vector(MyScenarioStatus(0.07,100000), MyScenarioStatus(0.06,55100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600), MyScenarioStatus(0.03,1065600)),Vector(MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300), MyScenarioStatus(0.12,1077900)),Vector(MyScenarioStatus(0.03,1065600), MyScenarioStatus(0.02,1022100), MyScenarioStatus(0.01,1074100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600)),Vector(MyScenarioStatus(0.07,55100)),Vector(MyScenarioStatus(0.06,55100))))
	Line 98599: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual marketBuys Vector(MyScenarioStatus(0.07,100000), MyScenarioStatus(0.06,55100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600), MyScenarioStatus(0.03,1065600))
	Line 98600: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual marketSells Vector(MyScenarioStatus(0.07,55100), MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300))
	Line 98601: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownBuyStatusesDefault Vector(MyScenarioStatus(0.03,1065600), MyScenarioStatus(0.02,1022100), MyScenarioStatus(0.01,1074100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600))
	Line 98602: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownSellStatusesDefault Vector(MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300), MyScenarioStatus(0.12,1077900))
	Line 98603: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownBuyStatusesDynamic Vector(MyScenarioStatus(0.06,55100))
	Line 98604: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownSellStatusesDynamic Vector(MyScenarioStatus(0.07,55100))
	Line 98605: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual dwMarketProjectedPrice 0.07
	Line 98606: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual dwMarketProjectedQty 55100
	Line 98607: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual signedDelta 0.009823354829507138
	Line 98608: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 3. Prediction residual: 541
	Line 98609: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 4. Absolute residual: 0
	Line 98610: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 5. Total residual: 541
	Line 98611: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 7. Shifted price: 121.00 from 118.5
	Line 98612: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 9. CustomId: CustomId(6lvM11aiY1ZXQp7), Total residual order: Order[id=null BUY null: 541 @ 121.0 val=DAY md=[0xe=CustomId(6lvM11aiY1ZXQp7)]]
	Line 98613: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 0. Start algo. Order with qty rounded: 500
	Line 98614: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 1. Live orders: List(RepoOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98614: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 1. Live orders: List(RepoOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98615: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 2a. Total residual: 500, live orders: 500, portfolio: INFINITY
	Line 98616: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 2e. Calculated qty is the same as live orders. Will do nothing.
	Line 98617: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 3. Algo result after price updated and rounding: List()
	Line 98618: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent. DW price & vol: BBL24C2201A@XBKK, price: Some(0.07), vol: Some(55100)
	Line 98619: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent:  pre-predictionresidual
	Line 98630: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 1. Dw List: List(DW(BBL24C2201A@XBKK,Some(0.07),Some(55100),Some(0.009823354829507138),Some(CALL),Vector(MyScenarioStatus(0.07,55100), MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300)),Vector(MyScenarioStatus(0.07,100000), MyScenarioStatus(0.06,55100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600), MyScenarioStatus(0.03,1065600)),Vector(MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300), MyScenarioStatus(0.12,1077900)),Vector(MyScenarioStatus(0.03,1065600), MyScenarioStatus(0.02,1022100), MyScenarioStatus(0.01,1074100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600)),Vector(MyScenarioStatus(0.07,55100)),Vector(MyScenarioStatus(0.06,55100))))
	Line 98631: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual marketBuys Vector(MyScenarioStatus(0.07,100000), MyScenarioStatus(0.06,55100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600), MyScenarioStatus(0.03,1065600))
	Line 98632: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual marketSells Vector(MyScenarioStatus(0.07,55100), MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300))
	Line 98633: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownBuyStatusesDefault Vector(MyScenarioStatus(0.03,1065600), MyScenarioStatus(0.02,1022100), MyScenarioStatus(0.01,1074100), MyScenarioStatus(0.05,1096200), MyScenarioStatus(0.04,1036600))
	Line 98634: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownSellStatusesDefault Vector(MyScenarioStatus(0.08,1076700), MyScenarioStatus(0.09,1005400), MyScenarioStatus(0.1,1059300), MyScenarioStatus(0.11,1074300), MyScenarioStatus(0.12,1077900))
	Line 98635: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownBuyStatusesDynamic Vector(MyScenarioStatus(0.06,55100))
	Line 98636: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual ownSellStatusesDynamic Vector(MyScenarioStatus(0.07,55100))
	Line 98637: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual dwMarketProjectedPrice 0.07
	Line 98638: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual dwMarketProjectedQty 55100
	Line 98639: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Prediction Residual signedDelta 0.009823354829507138
	Line 98640: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 3. Prediction residual: 541
	Line 98641: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 4. Absolute residual: 0
	Line 98642: 2021-12-08 14:29:12,787 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 5. Total residual: 541
	Line 98643: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 7. Shifted price: 121.00 from 118.5
	Line 98644: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 9. CustomId: CustomId(xNPVhVoYGq1NkFn), Total residual order: Order[id=null BUY null: 541 @ 121.0 val=DAY md=[0xe=CustomId(xNPVhVoYGq1NkFn)]]
	Line 98645: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 0. Start algo. Order with qty rounded: 500
	Line 98646: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 1. Live orders: List(RepoOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98646: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 1. Live orders: List(RepoOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98647: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 2a. Total residual: 500, live orders: 500, portfolio: INFINITY
	Line 98648: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 2e. Calculated qty is the same as live orders. Will do nothing.
	Line 98649: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 3. Algo result after price updated and rounding: List()
	Line 98650: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent. DW price & vol: BBL24C2201A@XBKK, price: Some(0.07), vol: Some(55100)
	Line 98651: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent:  pre-predictionresidual
	Line 98662: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 1. Dw List: List()
	Line 98663: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 3. Prediction residual: 0
	Line 98664: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 4. Absolute residual: 0
	Line 98665: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 5. Total residual: 0
	Line 98666: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 7. Shifted price: 121.00 from 118.5
	Line 98667: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 9. CustomId: CustomId(M4p1tMBa8BZKyZy), Total residual order: Order[id=null BUY null: 0 @ 121.0 val=DAY md=[0xe=CustomId(M4p1tMBa8BZKyZy)]]
	Line 98668: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 0. Start algo. Order with qty rounded: 0
	Line 98669: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 1. Live orders: List(RepoOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98669: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 1. Live orders: List(RepoOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98670: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 2a. Total residual: 0, live orders: 500, portfolio: INFINITY
	Line 98671: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 2d. Calculated qty is smaller than live orders. Will update or cancel.
	Line 98672: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 3. Algo result after price updated and rounding: List(CancelOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98672: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Algo 3. Algo result after price updated and rounding: List(CancelOrder(ActiveOrderDescriptorView[status=Active eStatus=Not executed act=INSERT resp=ACK eCode=0 error=null avgExecP=0.0 pOnMkt=121.0 qtyOnMkt=500 rQty=500 oCopy=Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]],Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]))
	Line 98673: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent 1200. Send Cancel Order: Order[id=ON-1-1638946412094-1 BUY null: 500 @ 121.0 val=DAY md=[0xe=LRl2Dof0DJdgFyA]]
	Line 98674: 2021-12-08 14:29:12,788 INFO  [efault-dispatcher-44] TRANSACTION-SET-EMAPI-HMM-PROXY: COMMAND ORDER/DELETE Order[ACTIVE id=ON-1-1638946412094-1 BUY BBL: 500 @ 121.0 val=DAY cum=0 uid=gqtrader2 ud=qekwx6dkgm01n md=[0x40000000=9901150 0x44020001=-47e7bcec:17d97f7f885:-7f9d:4:Agent 0x47009000=NORMAL 0x3510006=PRINCIPAL 0x47000027=51000336 0x44020002=-47e7bcec:17d97f7f885:-7f9d 0x2101001=500 0x3510100=false 0x40000100=DEFAULT 0x3510003=C924 0x3510004=0024_CU21_PG 0x351000a=false 0x47000031=gqtrader2 0x47000011=PRINCIPAL 0x351000c=TRANSPARENT 0x47000036=V 0x351000d=false 0x10003=gqtrader2 0x40000102=true 0x44020004=8185d312-d653-4e08-992f-08254936c60e 0x3510005=9901150 0x47000010=CASH 0x47000079=JV 0x3510002=0024 0x10064=SET-TRX 0x40000101=gqtrader2 0x21200009=true 0xe=LRl2Dof0DJdgFyA 0x44020005=mrt 0x1012e=ON-1-1638946412094-1 0x21010003=500.0 0x47009100=NONE 0x47000033=C924 0x3510101=0024_CU21@ 0x47000034=1638946412068 0x3510007=false 0x47000035=9446 0x47000029=9901150 0x47000028=1638946412068 0x3510008=false 0x3510001=9446 0x1012f=gqtrader2 0x3510009=false 0x47000001=DEALER 0x47000030=gqtrader2 0x1006a=1638946411222]]
	Line 98675: 2021-12-08 14:29:12,789 INFO  [efault-dispatcher-44] -47e7bcec:17d97f7f885:-7f9d:4:Agent: Agent. DW price & vol: BBL24C2201A@XBKK are empty


```
ERROR	Tue Dec 07 16:11:22 ICT 2021	strategy	feedback: from Summary for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
Filtered[Summary for SCGP@XBKK on SET-EMAPI-HMM-PROXY]: NumberFormatException: null
Filtered[Summary for SCGP@XBKK on SET-EMAPI-HMM-PROXY]
 Filtered[Pricing for EA13P2203A@XBKK/DEFAULT/TRADING/List(REFERENCE)]: NumberFormatException: null

Automaton Status for (SET-EMAPI-HMM-PROXY,DYNAMIC,EA24C2203A@XBKK,REFERENCE): NumberFormatException: null
Automaton Status for (SET-EMAPI-HMM-PROXY,DEFAULT,EA13P2203A@XBKK,REFERENCE): NumberFormatException: null
ERROR	Tue Dec 07 16:11:22 ICT 2021	controller	from Summary for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
ERROR	Tue Dec 07 16:11:22 ICT 2021	controller	from Depth for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
ERROR	Tue Dec 07 16:11:22 ICT 2021	controller	from Summary for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
ERROR	Tue Dec 07 16:11:09 ICT 2021	strategy	feedback: from Summary for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
ERROR	Tue Dec 07 16:11:08 ICT 2021	controller	from Summary for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
ERROR	Tue Dec 07 16:11:08 ICT 2021	controller	from Summary for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
ERROR	Tue Dec 07 16:11:08 ICT 2021	controller	from Depth for SCGP24C2203A@XBKK on SET-EMAPI-HMM-PROXY: NumberFormatException: null
```

```
java.lang.NumberFormatException
	at java.math.BigDecimal.<init>(BigDecimal.java:497)
	at java.math.BigDecimal.<init>(BigDecimal.java:827)
	at scala.math.BigDecimal$.decimal(BigDecimal.scala:51)
	at scala.math.BigDecimal$.apply(BigDecimal.scala:248)
	at guardian.Algo$.predictResidual(Algo.scala:453)
	at mrt.Agent.$anonfun$preProcess$6(Agent.scala:148)
	at mrt.Agent.$anonfun$preProcess$6$adapted(Agent.scala:137)
	at scala.collection.TraversableLike.$anonfun$map$1(TraversableLike.scala:234)
	at scala.collection.immutable.List.foreach(List.scala:389)
	at scala.collection.TraversableLike.map(TraversableLike.scala:234)
	at scala.collection.TraversableLike.map$(TraversableLike.scala:227)
	at scala.collection.immutable.List.map(List.scala:295)
	at mrt.Agent.$anonfun$preProcess$4(Agent.scala:137)
	at cats.data.EitherT.$anonfun$flatMap$1(EitherT.scala:405)
	at cats.package$$anon$1.flatMap(package.scala:73)
	at cats.data.EitherT.flatMap(EitherT.scala:403)
	at mrt.Agent.$anonfun$preProcess$3(Agent.scala:133)
	at cats.data.EitherT.$anonfun$flatMap$1(EitherT.scala:405)
	at cats.package$$anon$1.flatMap(package.scala:73)
	at cats.data.EitherT.flatMap(EitherT.scala:403)
	at mrt.Agent.$anonfun$preProcess$1(Agent.scala:123)
	at mrt.Agent.$anonfun$preProcess$1$adapted(Agent.scala:115)
	at cats.data.EitherT.$anonfun$flatMap$1(EitherT.scala:405)
	at cats.package$$anon$1.flatMap(package.scala:73)
	at cats.data.EitherT.flatMap(EitherT.scala:403)
	at mrt.Agent.preProcess(Agent.scala:115)
	at mrt.Agent.preProcess$(Agent.scala:113)
	at mrt.__ALG_TA__Agent.preProcess(Agent.scala:31)
	at mrt.Agent$$anonfun$2.$anonfun$applyOrElse$33(Agent.scala:320)
	at scala.Option.map(Option.scala:146)
	at mrt.Agent$$anonfun$2.$anonfun$applyOrElse$31(Agent.scala:320)
	at mrt.Agent$$anonfun$2.$anonfun$applyOrElse$31$adapted(Agent.scala:312)
	at algotrader.api.source.AbstractSource.$anonfun$notifyData$3(Source.scala:383)
	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:12)
	at algotrader.api.source.SourceContext.safely(SourceContext.scala:63)
	at algotrader.api.source.SourceContext.safely$(SourceContext.scala:61)
	at algotrader.api.internal.source.SourceContextImpl.safely(SourceContextImpl.scala:11)
	at algotrader.api.source.AbstractSource.$anonfun$notifyData$2(Source.scala:383)
	at scala.collection.Iterator.foreach(Iterator.scala:929)
	at scala.collection.Iterator.foreach$(Iterator.scala:929)
	at scala.collection.AbstractIterator.foreach(Iterator.scala:1417)
	at scala.collection.MapLike$DefaultValuesIterable.foreach(MapLike.scala:210)
	at algotrader.api.source.AbstractSource.$anonfun$notifyData$1(Source.scala:383)
	at algotrader.api.source.AbstractSource.$anonfun$notifyData$1$adapted(Source.scala:383)
	at algotrader.api.source.SelfInitOption.foreach(Source.scala:351)
	at algotrader.api.source.AbstractSource.notifyData(Source.scala:383)
	at algotrader.api.source.AbstractSource.notifyData$(Source.scala:382)
	at algotrader.api.source.ReactiveDropSource.notifyData(ReactiveDropSource.scala:35)
	at algotrader.api.source.ReactiveDropSource.handleData(ReactiveDropSource.scala:84)
	at algotrader.api.source.ReactiveDropSource.$anonfun$advancedScheduler$2(ReactiveDropSource.scala:53)
	at algotrader.api.source.ReactiveDropSource.$anonfun$advancedScheduler$2$adapted(ReactiveDropSource.scala:52)
	at algotrader.api.internal.source.DropScheduler.$anonfun$queueValue$1(AdvancedScheduler.scala:81)
	at algotrader.api.internal.source.SourceContextImpl.$anonfun$schedule$1(SourceContextImpl.scala:28)
	at horizontrader.plugins.scalascript.tradingagent.util.AkkaScheduling.$anonfun$invoke$1(StatefulSchedulings.scala:328)
	at horizontrader.plugins.scalascript.tradingagent.util.AkkaScheduling.$anonfun$invoke$1$adapted(StatefulSchedulings.scala:328)
	at horizontrader.plugins.scalascript.tradingagent.util.AgentActor$$anonfun$active$1.applyOrElse(StatefulSchedulings.scala:104)
	at akka.actor.Actor.aroundReceive(Actor.scala:484)
	at akka.actor.Actor.aroundReceive$(Actor.scala:484)
	at horizontrader.plugins.scalascript.tradingagent.util.AgentActor.aroundReceive(StatefulSchedulings.scala:87)
	at akka.actor.ActorCell.receiveMessage(ActorCell.scala:526)
	at akka.actor.ActorCell.invoke(ActorCell.scala:495)
	at akka.dispatch.Mailbox.processMailbox(Mailbox.scala:257)
	at akka.dispatch.Mailbox.run(Mailbox.scala:224)
	at akka.dispatch.Mailbox.exec(Mailbox.scala:234)
	at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
	at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
	at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
	at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)
	
	java.lang.NumberFormatException
	at java.math.BigDecimal.<init>(BigDecimal.java:497)
	at java.math.BigDecimal.<init>(BigDecimal.java:827)
	at scala.math.BigDecimal$.decimal(BigDecimal.scala:51)
	at scala.math.BigDecimal$.apply(BigDecimal.scala:248)
	at guardian.Algo$.predictResidual(Algo.scala:460)
	at mrt.Agent.$anonfun$preProcess$6(Agent.scala:156)
	at mrt.Agent.$anonfun$preProcess$6$adapted(Agent.scala:145)
	at scala.collection.TraversableLike.$anonfun$map$1(TraversableLike.scala:234)
	at scala.collection.immutable.List.foreach(List.scala:389)
	at scala.collection.TraversableLike.map(TraversableLike.scala:234)
	at scala.collection.TraversableLike.map$(TraversableLike.scala:227)
	at scala.collection.immutable.List.map(List.scala:295)
	
	at guardian.Algo$.predictResidual(Algo.scala:463)
	at guardian.Algo$.predictResidual(Algo.scala:470)
```