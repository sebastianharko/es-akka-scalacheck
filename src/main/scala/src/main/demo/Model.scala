package src.main.demo


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