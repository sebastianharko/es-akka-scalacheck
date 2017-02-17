import org.scalacheck.Gen
import org.scalacheck.Shapeless._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import src.main.demo.Commands._
import src.main.demo.{Command, UserAccount}

class SimplePropertySpec extends PropSpec with PropertyChecks with Matchers {

  val commandGen = Gen.containerOf[List, Command](Gen.oneOf(LogIn("default"), 
    LogIn("123"), 
    LogIn("password"), 
    LogIn("bla")))

  val suspendedUser = UserAccount(suspended = true)

  property("absolutely no way to login if the account is suspended - even if password is correct") {
    forAll(commandGen) { (commandList: List[Command]) =>
      suspendedUser.applyCommands(commandList).suspended shouldBe false
    }
  }

}
