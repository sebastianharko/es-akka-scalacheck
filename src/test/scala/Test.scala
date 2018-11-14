import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Gen._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import src.main.demo.Commands._
import src.main.demo.Entities._
import src.main.demo.EventSourcingSupport.ops._
import src.main.demo.{Command, UserAccount}

class SimplePropertySpec extends PropSpec with PropertyChecks with Matchers {

  val logInGen = {
    val randomPassword = for {
      v ← arbitrary[String]
    } yield LogIn(v)
    val correctPassword = const(LogIn("default"))
    Gen.nonEmptyListOf(Gen.oneOf(randomPassword, correctPassword))
  }



  property("absolutely no way to login if the account is suspended - even if password is correct") {
    val suspendedUser = UserAccount(suspended = true)
    forAll(logInGen) { commandList: List[Command] ⇒
      suspendedUser.applyCommands(commandList).numActiveLogins shouldBe 0
    }
  }

  property("no way to login if the password is incorrect") {
    val user = UserAccount(password = "summer200one", suspended = false)

    val logInCommandWithRandomPass = for {
      v ← arbitrary[String] if v != "summer200one"
    } yield LogIn(v)

    forAll(logInCommandWithRandomPass) {
      logIn ⇒ user.applyCommands(logIn :: Nil).numActiveLogins shouldBe 0
    }
  }

}
