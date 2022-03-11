

## Components
### Horizon
- sends market signals
- receives orders
### Hedger
- receives signals and caches it
- calculates price and quantity of total residual
- sends order
  The hedger stores market data and calculates partial and absolute residual. It will run until the bot receives market change signal.
### lib-algo-1-3
- stores active and pending orders
- recalculates the quantity
  The lib stores orders that are active. When total residual comes from Hedger, it will recalculate the quantity by subtracting it the quantity from live orders. If the value is positive it will send a new order if negative it will cancel some of the live orders and if zero it will not do anything.
  After an order is sent, it will be placed in *pending* list. Any new order will not be sent until this list is cleared. Once it receives *ack*, *nak* signal the order will be removed from the list and put in *live* list to be used in future calculations. *executed* signal will remove an order from *live*.

## Files
### hedger13v1
- Agent: stores market data and calculates total residual
- Controller: starts agent and receives user input and market status signals
- Lock: contains lock flag to stop the bot

### lib-algo-1-3-hedge
- Algo: contains the main logic for recalculating order qty and caches live and pending orders
- Entities: internal models
- Error : internal error models
- Repo: cache models

### What it needs:
- portfolioId
- hedgePortfolio
- ulInstrument
- hedgeInstrument

## Step: Starting the bot
When controller receives pre-market signal, it will send a message to Agent to create the core calculation object (algo).

## Step: Receiving data to sending orders
### Listen to market data updates
The bot listens to any update of these data:
- Ul projected price
- Portfolio qty / ul absolute residual
- DW delta price
- DW projected price and qty
- DW market buys and sells
- Default own orders
- Dynamic own orders
  Pro-processing applied is mainly to filter them for valid values. The result is stored in local variables.
  All these updates will trigger recalculation **except** default/dynamic own orders.
  Recalculation is started in `handleOnSignal`

### handleOnSignal

`handleOnSignal` contains:
####  `checkAndUpdatePendingOrderAndCalculation`
This method checks if pending caches are empty. There are two types of pending caches: pendingOrder which stores orders waiting for Ack/Nak and pendingCalculation that flags a request to calculate. If both are empty then calculation can start.
####  `process`
The core of the algorithm. It contains main methods `preProcess`  and `createOrderAction`.
#####  `preProcess`
Pre-process calculates prediction residual and absolute residual with cached market data in Agent. This method is a closure so the data it uses is a snapshot of the data when it was called.
Prediction residual is calculated in `Algo.preProcess` (check the implementation document). Prediction residual is then summed with absolute residual to become total residual. This value is already in the form of order with direction and positive price and quantity.
##### `createOrderAction`
This method compares total residual with live orders and make adjustment. If total residual is larger than the sum of all the quantities of live orders then it will create a new order with the new qty being the difference. Likewise if total residual is smaller then some orders will be cancelled or updated with reduced qty. Finally if total residual is the same then it will send an empty order. The result of this method is Order Action that has an action (create, update, or cancel) and the order.
Normally any order can be sent immediately to market. These orders are marked as `Immediate`. But when the direction changes then all live orders must be canceled first before a new order can be created. Not doing so in this order will cause a market error. In this scenario a new insert order is marked as `Later` and cached. After cancel orders are successfully executed the Later orders will be sent to market.
##### `updatePrice`
If total residual has different price than all order from `createOrderAction` and live orders will be updated. In case of order action only updateOrder and createOrder are affected by it.

Order actions will be rounded and rejected if they contain zero qty. The final result will be the result of `process`.
##### `pendingOrdersRepo#putImmediate,putLater`
Order actions from `process` will be cached in the local repo as *pending* according to their urgency. Any `Later` insert order will be filtered out.

###   processAndSend
This method will check Lock for any lock flag. It it's clear then it will send the orders to the market according to their action (create, update, cancel)

## Step: Handling Ack
### `handleOnOrderAck`
Order from ActiveOrderDescriptorView is put into *live* cache. This type is needed to update the order. Any order in *pending* caches is removed. If there is any order in Later cache, this order will be forwarded to `processAndSend`. If there is no more order in both Later or Immediate caches, it will check pendingCalculation cache if it should start a recalculation. If it's empty then the bot will not do anything.

## Step: Handling Nak and Rejected
### `handleOnOrderNak`
Order is removed from all *pending* caches and pendingCalculation is deactivated. The bot will not do anything else.

## Step: Handling Executed
The bot will not do anything as any necessary handling has been done in Ack step.

## Step: Stopping the bot
When Controller receives market status beside pre-close in UL or any of the DWs it will activate the lock in Lock object. `processAndSend` will need to check if the status of this lock to send any order to the market. Controller will also send a StopBot message to Agent to remove the calculation object (algo). This process will not be executed until the message queue in Agent is drained. However because the lock is already active any orders the bot makes during this time will not be sent to market.

## Overview
1. signal arrives
2. market data is updated in Hedger
3. Hedger calculates total residual
4. Total residual is sent to Lib
5. Lib uses live orders to recalculate new qty
6. The new order is placed in *pending* and sent to Hedger and to market
7. If *pending* is not empty, new total residual will not be processed by Lib but cached
8. Once the market sends Ack or Nak, Lib will move the order from *pending*.
9. In case of Nak, the order will be deleted. In case of Ack the order will be moved to *live* and if there was a request to recalculate, the calculation will start with current data.
10. If market sends *executed* the order will be removed from *live*
11. This process continues until the bot is stopped with market status signal.
 




