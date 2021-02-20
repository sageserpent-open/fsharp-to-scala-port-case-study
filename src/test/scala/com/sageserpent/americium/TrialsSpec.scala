package com.sageserpent.americium

import com.sageserpent.americium.java.{
  Trials => JavaTrials,
  TrialsApi => JavaTrialsApi
}
import org.scalamock.function.StubFunction1
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

import _root_.java.util.function.{Predicate, Function => JavaFunction}
import scala.collection.JavaConverters._
import scala.util.Try

class TrialsSpec
    extends FlatSpec
    with Matchers
    with MockFactory
    with TableDrivenPropertyChecks {
  autoVerify = false
  type TypeRequirementsToProtectCodeInStringsFromUnusedImportOptimisation =
    (JavaTrials[_], JavaFunction[_, _], Predicate[_])
  val api: TrialsApi         = TrialsImplementation
  val javaApi: JavaTrialsApi = TrialsImplementation

  "test driving the Scala API" should "not produce smoke" in {
    val trials = api.choose(2, -4, 3)

    val flatMappedTrials = trials flatMap (integer => api.only(1.1 * integer))

    flatMappedTrials.supplyTo(println)

    val mappedTrials = trials map (_ * 2.5)

    mappedTrials.supplyTo(println)

    api.alternate(flatMappedTrials, mappedTrials).supplyTo(println)

    api.choose(0 to 20).supplyTo(println)

    api.alternate(Seq(flatMappedTrials, mappedTrials)).supplyTo(println)

    api.choose(Array(1, 2, 3)).supplyTo(println)
  }

  "test driving the Java API" should "not produce smoke" in {
    val javaTrials = javaApi.choose(2, -4, 3)

    val flatMappedJavaTrials = javaTrials flatMap (integer =>
      javaApi.only(1.1 * integer))

    flatMappedJavaTrials.supplyTo(println)

    val mappedJavaTrials = javaTrials map (_ * 2.5)

    mappedJavaTrials.supplyTo(println)

    javaApi
      .alternate(flatMappedJavaTrials, mappedJavaTrials)
      .supplyTo(println)

    javaApi.choose((0 to 20).asJava).supplyTo(println)

    javaApi
      .alternate(Seq(flatMappedJavaTrials, mappedJavaTrials).asJava)
      .supplyTo(println)
  }

  "only one case" should "yield just one trial" in
    forAll(Table("case", 1, "foo", 2.3, List(false, 0, true))) { dataCase =>
      withExpectations {
        val sut = api.only(dataCase)

        val mockConsumer: StubFunction1[Any, Unit] = stubFunction[Any, Unit]

        sut.supplyTo(mockConsumer)

        mockConsumer.verify(dataCase)
      }
    }

  "only one case that provokes an exception" should "result in an exception that references it" in
    forAll(Table("case", 1, "foo", 2.3, Seq(false, 0, true))) { dataCase =>
      withExpectations {
        val sut = api.only(dataCase)

        val problem = new RuntimeException("Test problem")

        val mockConsumer = stubFunction[Any, Unit]

        mockConsumer.when(dataCase).throwing(problem)

        val exception = intercept[sut.TrialException] {
          sut.supplyTo(mockConsumer)
        }

        exception.getCause should be(problem)
        exception.provokingCase should be(dataCase)
      }
    }

  "a choice" should "yield all and only the cases given to it" in
    forAll(
      Table("possibleChoices",
            Seq.empty,
            1 to 10,
            -5 to 5 map (_.toString),
            Seq(true),
            Seq(4.3))) { possibleChoices =>
      withExpectations {
        val sut: Trials[Any] = api.choose(possibleChoices)

        val mockConsumer = stubFunction[Any, Unit]

        sut.supplyTo(mockConsumer)

        possibleChoices.foreach(possibleChoice =>
          mockConsumer.verify(possibleChoice))
      }
    }

  "a choice that includes exceptional cases" should "result in one of the corresponding exceptions" in {
    type ChoicesAndCriterion[X] = (Seq[X], X => Boolean)

    def testBodyInWildcardCapture[X](
        choicesAndCriterion: ChoicesAndCriterion[X]) =
      withExpectations {
        choicesAndCriterion match {
          case (possibleChoices, exceptionCriterion) =>
            val sut = api.choose(possibleChoices)

            val complainingConsumer = { caze: X =>
              if (exceptionCriterion(caze))
                throw ExceptionWithCasePayload(caze)
            }

            val exception = intercept[sut.TrialException] {
              sut.supplyTo(complainingConsumer)
            }

            val underlyingException = exception.getCause

            underlyingException shouldBe a[ExceptionWithCasePayload[_]]

            underlyingException match {
              case exceptionWithCasePayload: ExceptionWithCasePayload[_] =>
                exception.provokingCase should be(exceptionWithCasePayload.caze)

                exactly(1, possibleChoices) should be(
                  exceptionWithCasePayload.caze)
            }
        }
      }

    forAll(
      Table[ChoicesAndCriterion[_]](
        "possibleChoices -> exceptionCriterion",
        (1 to 10, 0 == (_: Int) % 2),
        (-5 to 5 map (_.toString), (_: String).contains("5")),
        (Seq(false, true), identity[Boolean] _),
        (Seq(4.3), (_: Double) => true)
      )) { choicesAndCriterion =>
      testBodyInWildcardCapture(choicesAndCriterion)
    }
  }

  "an alternation over finite alternatives" should "yield all and only the cases that would be yielded by its alternatives" in
    forAll(
      Table(
        "alternatives",
        Seq.empty,
        Seq(1 to 10),
        Seq(1 to 10, 20 to 30 map (_.toString)),
        Seq(1 to 10, Seq(true, false), 20 to 30),
        Seq(1, "3", 99),
        Seq(1 to 10, Seq(12), -3 to -1),
        Seq(Seq(0), 1 to 10, 13, -3 to -1)
      )) { alternatives =>
      withExpectations {
        val sut: Trials[Any] =
          api.alternate(alternatives map {
            case sequence: Seq[_] => api.choose(sequence)
            case singleton        => api.only(singleton)
          })

        val mockConsumer = stubFunction[Any, Unit]

        sut.supplyTo(mockConsumer)

        alternatives
          .flatMap {
            case several: Seq[_] => several
            case singleton       => Seq(singleton)
          }
          .foreach(possibleChoice => mockConsumer.verify(possibleChoice))
      }
    }

  "trials" should "yield repeatable cases" in
    forAll(
      Table("trails",
            api.only(1),
            api.choose(1, false, 99),
            api.alternate(api.choose(0 until 10 map (_.toString)),
                          api.choose(-10 until 0)))) { sut =>
      withExpectations {
        val mockConsumer = mockFunction[Any, Unit]

        // Whatever cases are supplied set the expectations...
        sut.supplyTo(mockConsumer.expects(_: Any): Unit)

        // ... now let's see if we see the same cases.
        sut.supplyTo(mockConsumer)
      }

    }

  case class ExceptionWithCasePayload[Case](caze: Case) extends RuntimeException

  they should "yield repeatable exceptions" in
    forAll(
      Table(
        "trails",
        api.only(JackInABox(1)),
        api.choose(1, false, JackInABox(99)),
        api.alternate(
          api.only(true),
          api.choose(0 until 10 map (_.toString) map JackInABox.apply),
          api.choose(-10 until 0))
      )) { sut =>
      withExpectations {
        val surprisedConsumer: Any => Unit = {
          case JackInABox(caze) => throw ExceptionWithCasePayload(caze)
          case _                =>
        }

        val exception = Try { sut.supplyTo(surprisedConsumer) }.failed.get
          .asInstanceOf[sut.TrialException]

        val exceptionFromSecondAttempt = Try { sut.supplyTo(surprisedConsumer) }.failed.get
          .asInstanceOf[sut.TrialException]

        exceptionFromSecondAttempt.provokingCase shouldBe exception.provokingCase
      }
    }

  case class JackInABox[Caze](caze: Caze)

  "mapping using a Java function" should "compile" in {
    assertCompiles("javaApi.only(1).map((_ + 1): JavaFunction[Int, Int])")
  }

  "mapping using a Scala function" should "compile" in {
    assertCompiles("api.only(1).map(_ + 1)")
  }

  "flatmapping using a Java function" should "compile" in {
    assertCompiles(
      "javaApi.only(1).flatMap(value => javaApi.choose(value, 1.0 + value))")
  }

  "flatmapping using a Scala function" should "compile" in {
    assertCompiles(
      "api.only(1).flatMap(value => api.choose(value, 1.0 + value))")
  }

  "filtering using a Java predicate" should "compile" in {
    assertCompiles("javaApi.only(1).filter(1 == _)")
  }

  "filtering using a Scala function" should "compile" in {
    assertCompiles("api.only(1).filter(1 == _)")
  }
}
