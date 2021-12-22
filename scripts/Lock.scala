package mrt

object Lock {
  @volatile var stopBots: Set[String] = Set.empty

  def getStopBot(ul: String): Boolean = stopBots.contains(ul)

  def addStopBot(ul: String): Unit = stopBots = stopBots + ul

  def removeStopBot(ul: String): Unit = stopBots = stopBots - ul
}
