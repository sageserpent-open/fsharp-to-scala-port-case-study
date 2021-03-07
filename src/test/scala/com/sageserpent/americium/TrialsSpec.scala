package com.sageserpent.americium

import com.sageserpent.americium.java.{
  Trials => JavaTrials,
  TrialsApi => JavaTrialsApi
}
import org.scalamock.function.StubFunction1
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

import _root_.java.util.UUID
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

  val limit: Int = 500

  "test driving the Scala API" should "not produce smoke" in {
    val trials = api.choose(2, -4, 3)

    val flatMappedTrials = trials flatMap (integer => api.only(1.1 * integer))

    flatMappedTrials.withLimit(limit).supplyTo(println)

    val mappedTrials = trials map (_ * 2.5)

    mappedTrials.withLimit(limit).supplyTo(println)

    api
      .alternate(flatMappedTrials, mappedTrials)
      .withLimit(limit)
      .supplyTo(println)

    api
      .choose(0 to 20)
      .withLimit(limit)
      .supplyTo(println)

    api
      .alternate(Seq(flatMappedTrials, mappedTrials))
      .withLimit(limit)
      .supplyTo(println)

    api
      .choose(Array(1, 2, 3))
      .withLimit(limit)
      .supplyTo(println)

    api
      .stream(_.toString)
      .withLimit(limit)
      .supplyTo(println)

    Trials.integers
      .withLimit(limit)
      .supplyTo(println)

    Trials.longs
      .withLimit(limit)
      .supplyTo(println)

    Trials.doubles
      .withLimit(limit)
      .supplyTo(println)

    Trials.coinFlip
      .withLimit(limit)
      .supplyTo(println)

    Trials.trueOrFalse
      .withLimit(limit)
      .supplyTo(println)
  }

  "test driving the Java API" should "not produce smoke" in {
    val javaTrials = javaApi.choose(2, -4, 3)

    val flatMappedJavaTrials = javaTrials flatMap (integer =>
      javaApi.only(1.1 * integer))

    flatMappedJavaTrials.withLimit(limit).supplyTo(println)

    val mappedJavaTrials = javaTrials map (_ * 2.5)

    mappedJavaTrials.withLimit(limit).supplyTo(println)

    javaApi
      .alternate(flatMappedJavaTrials, mappedJavaTrials)
      .withLimit(limit)
      .supplyTo(println)

    javaApi
      .choose((0 to 20).asJava)
      .withLimit(limit)
      .supplyTo(println)

    javaApi
      .alternate(Seq(flatMappedJavaTrials, mappedJavaTrials).asJava)
      .withLimit(limit)
      .supplyTo(println)

    javaApi
      .stream(_.toString)
      .withLimit(limit)
      .supplyTo(println)

    JavaTrials.integers
      .withLimit(limit)
      .supplyTo(println)

    JavaTrials.longs
      .withLimit(limit)
      .supplyTo(println)

    JavaTrials.doubles
      .withLimit(limit)
      .supplyTo(println)

    JavaTrials.coinFlip
      .withLimit(limit)
      .supplyTo(println)

    JavaTrials.trueOrFalse
      .withLimit(limit)
      .supplyTo(println)
  }

  "only one case" should "yield just one trial" in
    forAll(Table("case", 1, "foo", 2.3, List(false, 0, true))) { dataCase =>
      withExpectations {
        val sut = api.only(dataCase)

        val mockConsumer: StubFunction1[Any, Unit] = stubFunction[Any, Unit]

        sut.withLimit(limit).supplyTo(mockConsumer)

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
          sut.withLimit(limit).supplyTo(mockConsumer)
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

        sut.withLimit(limit).supplyTo(mockConsumer)

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
              sut.withLimit(limit).supplyTo(complainingConsumer)
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

        sut.withLimit(limit).supplyTo(mockConsumer)

        alternatives
          .flatMap {
            case several: Seq[_] => several
            case singleton       => Seq(singleton)
          }
          .foreach(possibleChoice => mockConsumer.verify(possibleChoice))
      }
    }

  "an alternation" should "yield cases that satisfy an invariant of one of its alternatives" in
    forAll(
      Table(
        "alternatives",
        Seq.empty,
        Seq(1 to 10),
        Seq(1 to 10, 20 to 30 map (_.toString)),
        Seq(1 to 10, Seq(true, false), 20 to 30),
        Seq(1, "3", 99),
        Seq(1 to 10, Seq(12), -3 to -1),
        Seq(Seq(0), 1 to 10, 13, -3 to -1),
        Seq((_: Long).toString),
        Seq(Seq(0), 1 to 10, 13, identity[Long] _, -3 to -1)
      )) { alternatives =>
      withExpectations {
        val alternativeIds = Vector.fill(alternatives.size) {
          UUID.randomUUID()
        }

        def predicateOnHash(caze: Any) = 0 == caze.hashCode() % 3

        val sut: Trials[(Any, UUID)] =
          api.alternate(alternatives map {
            case sequence: Seq[_] => api.choose(sequence)
            case factory: (Long => Any) =>
              api.stream(factory)
            case singleton => api.only(singleton)
          } zip alternativeIds map {
            // Set up a unique invariant on each alternative - it should supply pairs,
            // each of which has the same id component that denotes the alternative. As
            // the id is unique, the implementation of `Trials.alternative` cannot fake
            // the id values - so they must come from the alternatives somehow. Furthermore,
            // the pair should satisfy a predicate on its hash.
            case (trials, id) => trials.map(_ -> id).filter(predicateOnHash)
          })

        val mockConsumer = mockFunction[(Any, UUID), Unit]

        mockConsumer
          .expects(where { identifiedCase: (Any, UUID) =>
            alternativeIds.contains(identifiedCase._2) && predicateOnHash(
              identifiedCase)
          })
          .anyNumberOfTimes()

        sut.withLimit(limit).supplyTo(mockConsumer)
      }
    }

  "trials" should "yield repeatable cases" in
    forAll(
      Table(
        "trails",
        api.only(1),
        api.choose(1, false, 99),
        api.alternate(api.choose(0 until 10 map (_.toString)),
                      api.choose(-10 until 0)),
        api.stream(_ * 1.46),
        api.alternate(api.stream(_ * 1.46),
                      api.choose(0 until 10 map (_.toString)),
                      api.choose(-10 until 0))
      )) { sut =>
      withExpectations {
        val mockConsumer = mockFunction[Any, Unit]

        // Whatever cases are supplied set the expectations...
        sut.withLimit(limit).supplyTo(mockConsumer.expects(_: Any): Unit)

        // ... now let's see if we see the same cases.
        sut.withLimit(limit).supplyTo(mockConsumer)
      }
    }

  they should "produce no more than the limiting number of cases" in
    forAll(
      Table(
        "trails"                 -> "limit",
        api.only(1)              -> 1,
        api.choose(1, false, 99) -> 3,
        api.alternate(api.choose(0 until 10 map (_.toString)),
                      api.choose(-10 until 0)) -> 4,
        api.stream(identity)                   -> 200
      )
    ) { (sut, limit) =>
      withExpectations {
        val mockConsumer = stubFunction[Any, Unit]

        sut.withLimit(limit).supplyTo(mockConsumer)

        mockConsumer.verify(*).repeat(1 to limit)
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
          api.choose(-10 until 0)),
        api.stream({
          case value if 0 == value % 3 => JackInABox(value)
          case value => value
        }),
        api.alternate(api.only(true),
                      api.choose(-10 until 0),
                      api.stream(JackInABox.apply)),
      )) { sut =>
      withExpectations {
        val surprisedConsumer: Any => Unit = {
          case JackInABox(caze) => throw ExceptionWithCasePayload(caze)
          case _                =>
        }

        val exception = Try { sut.withLimit(limit).supplyTo(surprisedConsumer) }.failed.get
          .asInstanceOf[sut.TrialException]

        val exceptionFromSecondAttempt = Try {
          sut.withLimit(limit).supplyTo(surprisedConsumer)
        }.failed.get
          .asInstanceOf[sut.TrialException]

        exceptionFromSecondAttempt.provokingCase shouldBe exception.provokingCase
      }
    }

  case class JackInABox[Caze](caze: Caze)

  "an exceptional case" should "be reproduced via its recipe" in forAll(
    Table(
      "trails",
      api.only(JackInABox(1)),
      api.choose(1, false, JackInABox(99)),
      api.alternate(
        api.only(true),
        api.choose(0 until 10 map (_.toString) map JackInABox.apply),
        api.choose(-10 until 0)),
      api.alternate(
        api.only(true),
        api.choose(-10 until 0),
        api.alternate(api.choose(-99 to -50), api.only(JackInABox(-2)))
      ),
      api.alternate(
        api.only(true),
        api.alternate(api.choose(-99 to -50),
                      api.choose("Red herring", false, JackInABox(-2))),
        api.choose(-10 until 0)
      ),
      api.stream({
        case value if 0 == value % 3 => JackInABox(value)
        case value => value
      }),
      api.alternate(
        api.only(true),
        api.stream({
          case value if 0 == value % 3 => JackInABox(value)
          case value => value
        }),
        api.choose(-10 until 0)
      )
    )) { sut =>
    withExpectations {
      val surprisedConsumer: Any => Unit = {
        case JackInABox(caze) => throw ExceptionWithCasePayload(caze)
        case _                =>
      }

      val exception = Try { sut.withLimit(limit).supplyTo(surprisedConsumer) }.failed.get
        .asInstanceOf[sut.TrialException]

      val exceptionRecreatedViaRecipe = Try {
        sut.supplyTo(exception.recipe, surprisedConsumer)
      }.failed.get
        .asInstanceOf[sut.TrialException]

      exceptionRecreatedViaRecipe.provokingCase shouldBe exception.provokingCase
      exceptionRecreatedViaRecipe.recipe shouldBe exception.recipe
    }
  }

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
