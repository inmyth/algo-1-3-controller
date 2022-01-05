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
- ScenarioStatus can be null, its fields like priceOnMarket and qtyOnMarket can be null or NaN
- horizon delta precision is 15 (e.g 7.072637332864818E-4)
- object is shared between traits (and ul)
```
marketSells Vector(MyScenarioStatus(0.18,35400), MyScenarioStatus(0.19,1056500), MyScenarioStatus(0.2,1032500), MyScenarioStatus(0.21,1020900), MyScenarioStatus(0.22,1042400))
marketSells Vector(MyScenarioStatus(0.19,1056500), MyScenarioStatus(0.2,1032500), MyScenarioStatus(0.21,1020900), MyScenarioStatus(0.22,1042400))
```
- some qty can be missing (=0)
```
Dw List: List(DW(CPAL24C2203A@XBKK,Some(0.25),Some(300000),Some(0.040089664162088465),Some(CALL),Vector(MyScenarioStatus(0.24,36000), MyScenarioStatus(0.25,1010600), MyScenarioStatus(0.26,1088900), MyScenarioStatus(0.27,1025200), MyScenarioStatus(0.28,1048800)),Vector(MyScenarioStatus(0.25,300000), MyScenarioStatus(0.24,100000), MyScenarioStatus(0.23,36000), MyScenarioStatus(0.22,1070900), MyScenarioStatus(0.21,1061800)),Vector(MyScenarioStatus(0.27,1025200), MyScenarioStatus(0.28,1048800), MyScenarioStatus(0.29,1016300), MyScenarioStatus(0.25,1010600), MyScenarioStatus(0.26,1088900)),Vector(MyScenarioStatus(0.21,1061800), MyScenarioStatus(0.20,1090400), MyScenarioStatus(0.19,1009800), MyScenarioStatus(0.18,1018100), MyScenarioStatus(0.22,1070900)),Vector(MyScenarioStatus(0.24,36000)),Vector(MyScenarioStatus(0.23,36000))))
Dw List: List(DW(CPAL24C2203A@XBKK,Some(0.25),Some(300000),Some(0.040089664162088465),Some(CALL),Vector(MyScenarioStatus(0.24,36000), MyScenarioStatus(0.25,1010600), MyScenarioStatus(0.26,1088900), MyScenarioStatus(0.27,1025200), MyScenarioStatus(0.28,1048800)),Vector(MyScenarioStatus(0.25,300000), MyScenarioStatus(0.24,100000), MyScenarioStatus(0.23,36000), MyScenarioStatus(0.22,1070900), MyScenarioStatus(0.21,1061800)),Vector(MyScenarioStatus(0.27,1025200), MyScenarioStatus(0.28,1048800), MyScenarioStatus(0.29,1016300), MyScenarioStatus(0.25,1010600), MyScenarioStatus(0.26,1088900)),Vector(MyScenarioStatus(0.21,1061800), MyScenarioStatus(0.20,1090400), MyScenarioStatus(0.19,1009800), MyScenarioStatus(0.18,1018100), MyScenarioStatus(0.22,1070900)),Vector(MyScenarioStatus(0.24,0)),Vector(MyScenarioStatus(0.23,36000))))
```
- change from pre open to open
SEQ
1.DW Stop Bot Signal
2.Reset DW Projected Price & Proj Qty
3.Reset DW Market Buy Sell
4.1 Update Dynamic Own Sell
4.2 Update Dynamic Own Buy
- own can have more qty and price level that doesnt exist in market 

- [x] stop the bot when dw opens before ul (executed signal)


## Creating new Gradle project
1. Horizon > create new strategy > Generate IDEA files 
2. Go to Idea > Open Gradle Tab > Click Refresh

### Give Package Name !





