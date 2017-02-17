package src.main.demo

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

object AkkaPersistenceActorForEntity {

  def props(entityId: String, initialState: EntityState): Props = {
    Props(new AkkaPersistenceActorForEntity(entityId, initialState))
  }

}

class AkkaPersistenceActorForEntity(entityId: String, initialState: EntityState) extends PersistentActor with ActorLogging {

  var entityState: EntityState = initialState

  override def receiveRecover: Receive = {
    case e: Event =>
      log.info("recovering event {}", e.toString)
      entityState = entityState.applyEvent(e)
    case RecoveryCompleted =>
      log.info("recovery completed!")
  }

  override def receiveCommand: Receive = {
    case c: Command =>
      val events = initialState.applyCommand(c)
      persistAll(events) { (e: Event) =>
        log.info("persisting event {}", e.toString)
        entityState = entityState.applyEvent(e)
      }
  }

  override def persistenceId: String = entityId

}

trait Event

trait Command

trait EntityState {

  def applyEvent(event: Event): EntityState

  def applyEvents(events: List[Event]): EntityState  = {
    events.foldLeft(this)((state, event) => state.applyEvent(event))
  }

  def applyCommand(command: Command): List[Event]

  def applyCommands(commands: List[Command]): EntityState = {
    commands.foldLeft(this)((state: EntityState, command: Command) => state.applyEvents(applyCommand(command)))
  }

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
                       numFailedLoginsAttempts: Int = 0
                    ) extends EntityState {

  import Events._
  import Commands._
  override def applyEvent(event: Event): EntityState = {

    event match {

      case UserLoggedIn =>
        this.copy(numActiveLogins = numActiveLogins + 1)

      case UserLoggedOut =>
        this.copy(numActiveLogins = numActiveLogins - 1)

      case UserAuthFailed =>
        this.copy(numFailedLoginsAttempts = numFailedLoginsAttempts + 1)

      case AccountSuspended =>
        this.copy(suspended = true)

      case AccountReactivated =>
        this.copy(suspended = false)

      case PremiumAccountEnabled =>
        this.copy(accountType = Premium)

      case PremiumAccountDisabled =>
        this.copy(accountType = Free)
    }
  }

  override def applyCommand(command: Command) = command match {

    case EnablePremiumAccount =>
      // normally, here we do some external API calls
      val validCreditCardPayment = true
      if (validCreditCardPayment)
        List(PremiumAccountEnabled)
      else
        List()

    case DisablePremiumAccount =>
      if (accountType == Premium)
       List(PremiumAccountDisabled)
      else
        List()

    case UsePremiumFeature =>
      if (accountType == Premium)
        List(PremiumFeatureBeingUsed)
      else
        List()

    case LogIn(providedPassword) =>
      if (!suspended) {
        if (providedPassword == password)
          List(UserLoggedIn)
        else
          if (numFailedLoginsAttempts == 3)
            // cancel every active session
            List.fill(numActiveLogins)(UserLoggedOut) :+ AccountSuspended
          else
            List(UserAuthFailed)
      } else List()

    case LogOut =>
      if (numActiveLogins > 0)
       List(UserLoggedOut)
      else
        List()
  }

}

object Main extends App {

  implicit val system = ActorSystem("system")

  val account = new UserAccount()

  val actorRef = system.actorOf(AkkaPersistenceActorForEntity.props("UserId-#1", initialState = account))

  actorRef ! Commands.LogIn("123")
  actorRef ! Commands.LogIn("password")

  actorRef ! PoisonPill

  Thread.sleep(9000)

  val newActorRef = system.actorOf(AkkaPersistenceActorForEntity.props("UserId-#1", initialState = account))

}