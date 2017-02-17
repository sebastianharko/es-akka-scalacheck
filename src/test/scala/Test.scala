import org.scalatest.{Matchers, PropSpec}
import org.scalatest.prop.{PropertyChecks}

class SimplePropertySpec extends PropSpec with PropertyChecks with Matchers {

  property("String should append each other with the concat method") {
    forAll { (a:String, b:String) =>
      a.concat(b) should be (a + b)
    }
  }

}
