package mrt

import algotrader.api.Messages.{Load, Start}
import algotrader.api.NativeController
import horizontrader.plugins.hmm.connections.service.IDictionaryProvider
import horizontrader.services.instruments.InstrumentDescriptor

import scala.language.higherKinds

trait Controller extends NativeController {
  val portfolioId: String
  val hedgePortfolio: String
  val ulInstrument: InstrumentDescriptor
  val hedgeInstrument: InstrumentDescriptor //PTT@XBKK ?? Nop will find a way // String => SET-EMAPI-HMM-PROXY|ADVANC@XBKK
  val dictionaryService: IDictionaryProvider = getService[IDictionaryProvider]

  var agent: TradingAgentComponent = null

  onMessage {

    case Load =>
      agent = createTradingAgent(
        "Agent.scala",
        Map(
          "portfolioId"       -> portfolioId,
          "hedgePortfolio"    -> hedgePortfolio,
          "ulInstrument"      -> ulInstrument,
          "hedgeInstrument"   -> hedgeInstrument,
          "dictionaryService" -> dictionaryService
        )
      )

    case Start =>
  }

}
