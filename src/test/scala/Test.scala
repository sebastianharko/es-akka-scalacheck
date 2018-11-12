import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import src.main.demo.Commands._
import src.main.demo.{Command, EventSourceableEntities, UserAccount}

import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

class SimplePropertySpec extends PropSpec with PropertyChecks with Matchers {

  val logInGen = {
    val randomPassword = for {
      v ← arbitrary[String]
    } yield LogIn(v)
    val correctPassword = const(LogIn("default"))
    Gen.nonEmptyListOf(Gen.oneOf(randomPassword, correctPassword))
  }

  import EventSourceableEntities.{EsUserAccount ⇒ es}

  property("absolutely no way to login if the account is suspended - even if password is correct") {
    val suspendedUser = UserAccount(suspended = true)
    forAll(logInGen) { commandList: List[Command] ⇒
      es.applyCommands(suspendedUser, commandList).numActiveLogins shouldBe 0
    }
  }

  property("no way to login if the password is incorrect") {
    val user = UserAccount(password = "summer200one", suspended = false)

    val logInCommandWithRandomPass = for {
      v ← arbitrary[String]
    } yield LogIn(v)

    forAll(logInCommandWithRandomPass) {
      logIn ⇒ es.applyCommands(user, logIn :: Nil).numActiveLogins shouldBe 0
    }
  }

}
