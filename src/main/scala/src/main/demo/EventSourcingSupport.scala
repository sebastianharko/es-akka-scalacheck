package src.main.demo

import simulacrum._

@typeclass trait EventSourcingSupport[T] {

  def applyEvent(t: T, e: Event): T

  def applyCommand(t: T, c: Command): (ResponseDocument, List[Event])

  def applyEvents(t: T, events: List[Event]): T =
    events.foldLeft(t)((state, event) => applyEvent(state, event))

  def applyCommands(t: T, commands: List[Command]): T =
    commands.foldLeft(t)((state: T, command: Command) => applyEvents(state, applyCommand(state, command)._2))

}