package src.main.demo

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import src.main.demo.ResponseDocument.NoResponseForSender

sealed trait EventSourceable[T] {

  def applyEvent(t: T, e: Event): T

  def applyCommand(t: T, c: Command): (ResponseDocument, List[Event])

  def applyEvents(t: T, events: List[Event]): T =
    events.foldLeft(t)((state, event) => applyEvent(state, event))

  def applyCommands(t: T, commands: List[Command]): T =
    commands.foldLeft(t)((state: T, command: Command) => applyEvents(state, applyCommand(state, command)._2))

}

object EventSourceableEntities {

  implicit object EsUserAccount extends EventSourceable[UserAccount] {

    import Events._
    import Commands._
    import ResponseDocument.NoResponseForSender

    def applyEvent(t: UserAccount, event: Event): UserAccount = {

      event match {

        case UserLoggedIn =>
          t.copy(numActiveLogins = t.numActiveLogins + 1)

        case UserLoggedOut =>
          t.copy(numActiveLogins = t.numActiveLogins - 1)

        case UserAuthFailed =>
          t.copy(numFailedLoginsAttempts = t.numFailedLoginsAttempts + 1)

        case AccountSuspended =>
          t.copy(suspended = true)

        case AccountReactivated =>
          t.copy(suspended = false)

        case PremiumAccountEnabled =>
          t.copy(accountType = Premium)

        case PremiumAccountDisabled =>
          t.copy(accountType = Free)

        case PremiumFeatureBeingUsed =>
          t

      }
    }

    override def applyCommand(t: UserAccount, command: Command) = command match {

      case EnablePremiumAccount if !t.suspended =>
        // normally, here we do some external API calls
        val validCreditCardPayment = true
        if (validCreditCardPayment)
          (NoResponseForSender, List(PremiumAccountEnabled))
        else
          (NoResponseForSender, List())

      case DisablePremiumAccount if !t.suspended =>
        if (t.accountType == Premium)
          (NoResponseForSender, List(PremiumAccountDisabled))
        else
          (NoResponseForSender, List())

      case UsePremiumFeature if !t.suspended && t.numActiveLogins > 0 =>
        if (t.accountType == Premium)
          (NoResponseForSender, List(PremiumFeatureBeingUsed))
        else
          (NoResponseForSender, List())

      case LogIn(providedPassword) if !t.suspended =>
        if (providedPassword == t.password)
          (NoResponseForSender, List(UserLoggedIn))
        else if (t.numFailedLoginsAttempts == 3)
        // cancel every active session
          (NoResponseForSender, List.fill(t.numActiveLogins)(UserLoggedOut) :+ AccountSuspended)
        else
          (NoResponseForSender, List(UserAuthFailed))


      case LogOut =>
        if (t.numActiveLogins > 0)
          (NoResponseForSender, List(UserLoggedOut))
        else
          (NoResponseForSender, List())

      case ReActivateAccount(secretCode) if t.suspended && secretCode == "V6G1J1" =>
        (NoResponseForSender, List(AccountReactivated))

      case _ => (NoResponseForSender, List())
    }

  }

}


object AkkaPersistenceActorForEntity {

  def props[T](entityId: String, initialEntityState: T)(implicit es:EventSourceable[T]): Props = {
    Props(new AkkaPersistenceActorForEntity(entityId, initialEntityState))
  }
}



class AkkaPersistenceActorForEntity[T](entityId: String, initialEntityState: T)(implicit es: EventSourceable[T]) extends PersistentActor with ActorLogging {

  var entityState: T = initialEntityState

  override def receiveRecover: Receive = {
    case e: Event =>
      log.info("recovering event {}", e.toString)
      entityState = es.applyEvent(entityState, e)
    case RecoveryCompleted =>
      log.info("recovery completed!")
  }

  override def receiveCommand: Receive = {
    case c: Command =>
      val commandResult = es.applyCommand(entityState, c)
      val response = commandResult._1
      val events = commandResult._2
      persistAll(events) { (e: Event) =>
        log.info("persisting event {}", e.toString)
        entityState = es.applyEvent(entityState, e)
        if (response != NoResponseForSender) {
          sender ! response
        }
      }
  }

  override def persistenceId: String = entityId

}

sealed trait ResponseDocument

sealed trait Command

sealed trait Event

object ResponseDocument {

  case object NoResponseForSender extends ResponseDocument

}

object Commands {

  case object EnablePremiumAccount extends Command

  case object DisablePremiumAccount extends Command

  case class LogIn(password: String) extends Command

  case object LogOut extends Command

  case class ReActivateAccount(secretCode: String) extends Command

  case object UsePremiumFeature extends Command

}

object Events {

  case object PremiumAccountEnabled extends Event

  case object PremiumAccountDisabled extends Event

  case object PremiumFeatureBeingUsed extends Event

  case object AccountSuspended extends Event

  case object AccountReactivated extends Event

  case object UserLoggedIn extends Event

  case object UserLoggedOut extends Event

  case object UserAuthFailed extends Event

}


sealed trait AccountType
case object Free extends AccountType
case object Premium extends AccountType

case class UserAccount(accountType: AccountType = Free,
                       password: String = "default",
                       suspended: Boolean = false,
                       numActiveLogins: Int = 0,
                       numFailedLoginsAttempts: Int = 0)

object Main extends App {

  implicit val system = ActorSystem("system")

  val account = new UserAccount()

  import EventSourceableEntities.EsUserAccount
  val actorRef = system.actorOf(AkkaPersistenceActorForEntity.props("UserId-#1", initialEntityState = account))

  actorRef ! Commands.LogIn("123")
  actorRef ! Commands.LogIn("password")

  Thread.sleep(9000)

  actorRef ! PoisonPill

  val newActorRef = system.actorOf(AkkaPersistenceActorForEntity.props("UserId-#1", initialEntityState = account))

}