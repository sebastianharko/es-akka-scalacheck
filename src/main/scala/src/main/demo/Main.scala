package src.main.demo

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import src.main.demo.ResponseDocument.NoResponseForSender

object Entities {

  implicit val esUserAccount = new EventSourcingSupport[UserAccount] {

    import Commands._
    import Events._
    import ResponseDocument.NoResponseForSender

    def applyEvent(t: UserAccount, event: Event): UserAccount = {

      event match {

        case UserLoggedIn ⇒
          t.copy(numActiveLogins = t.numActiveLogins + 1)

        case UserLoggedOut ⇒
          t.copy(numActiveLogins = t.numActiveLogins - 1)

        case UserAuthFailed ⇒
          t.copy(numFailedLoginsAttempts = t.numFailedLoginsAttempts + 1)

        case AccountSuspended ⇒
          t.copy(suspended = true)

        case AccountReactivated ⇒
          t.copy(suspended = false)

        case PremiumAccountEnabled ⇒
          t.copy(accountType = Premium)

        case PremiumAccountDisabled ⇒
          t.copy(accountType = Free)

        case PremiumFeatureBeingUsed ⇒
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


class UserAccountActor extends PersistentActor with ActorLogging {

  var userState = new UserAccount()

  import EventSourcingSupport.ops._
  import Entities._

  override def persistenceId: String = "user-id"

  override def receiveCommand: Receive = {
    case c: Command ⇒
      val commandResult = userState.applyCommand(c)
      val response = commandResult._1
      val events = commandResult._2
      persistAll(events) { (e: Event) ⇒
        log.info("persisting event {}", e.toString)
        userState = userState.applyEvent(e)
        if (response != NoResponseForSender) {
          sender ! response
        }
      }
  }

  override def receiveRecover: Receive = {
    case e: Event ⇒
      log.info("recovering event {}", e.toString)
      userState = userState.applyEvent(e)
    case RecoveryCompleted ⇒
      log.info("recovery completed!")
  }

}


object Main extends App {

  implicit val system = ActorSystem("system")

  val actorRef = system.actorOf(Props(new UserAccountActor()))

  actorRef ! Commands.LogIn("123")
  actorRef ! Commands.LogIn("password")

  Thread.sleep(9000)

  actorRef ! PoisonPill

}