package com.sageserpent.americium.java

import cats.data.{OptionT, State, StateT}
import cats.free.Free
import cats.free.Free.{liftF, pure}
import cats.implicits._
import cats.{Eval, Traverse, ~>}
import com.google.common.collect.{Ordering => _, _}
import com.sageserpent.americium.Trials.RejectionByInlineFilter
import com.sageserpent.americium.java.tupleTrials.{
  Tuple2Trials => JavaTuple2Trials
}
import com.sageserpent.americium.java.{
  Trials => JavaTrials,
  TrialsApi => JavaTrialsApi
}
import com.sageserpent.americium.randomEnrichment.RichRandom
import com.sageserpent.americium.tupleTrials.Tuple2Trials
import com.sageserpent.americium.{Trials, TrialsApi}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import _root_.java.lang.{
  Boolean => JavaBoolean,
  Byte => JavaByte,
  Character => JavaCharacter,
  Double => JavaDouble,
  Integer => JavaInteger,
  Iterable => JavaIterable,
  Long => JavaLong
}
import _root_.java.time.Instant
import _root_.java.util.function.{
  Consumer,
  Predicate,
  Supplier,
  Function => JavaFunction
}
import _root_.java.util.{
  Optional,
  Comparator => JavaComparator,
  Iterator => JavaIterator,
  List => JavaList,
  Map => JavaMap
}
import scala.collection.immutable.{SortedMap, SortedSet}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Random

object TrialsImplementation {
  val defaultComplexityWall = 100

  type DecisionStages   = List[Decision]
  type Generation[Case] = Free[GenerationOperation, Case]

  val javaApi = new CommonApi with JavaTrialsApi {
    override def delay[Case](
        delayed: Supplier[JavaTrials[Case]]
    ): JavaTrials[Case] = scalaApi.delay(delayed.get().scalaTrials)

    override def choose[Case](
        choices: JavaIterable[Case]
    ): TrialsImplementation[Case] =
      scalaApi.choose(choices.asScala)

    override def choose[Case](
        choices: Array[Case with AnyRef]
    ): TrialsImplementation[Case] =
      scalaApi.choose(choices.toSeq)

    override def chooseWithWeights[Case](
        firstChoice: JavaMap.Entry[JavaInteger, Case],
        secondChoice: JavaMap.Entry[JavaInteger, Case],
        otherChoices: JavaMap.Entry[JavaInteger, Case]*
    ): TrialsImplementation[Case] =
      scalaApi.chooseWithWeights(
        (firstChoice +: secondChoice +: otherChoices) map (entry =>
          Int.unbox(entry.getKey) -> entry.getValue
        )
      )

    override def chooseWithWeights[Case](
        choices: JavaIterable[JavaMap.Entry[JavaInteger, Case]]
    ): TrialsImplementation[Case] =
      scalaApi.chooseWithWeights(
        choices.asScala.map(entry => Int.unbox(entry.getKey) -> entry.getValue)
      )

    override def chooseWithWeights[Case](
        choices: Array[JavaMap.Entry[JavaInteger, Case]]
    ): TrialsImplementation[Case] =
      scalaApi.chooseWithWeights(
        choices.toSeq.map(entry => Int.unbox(entry.getKey) -> entry.getValue)
      )

    override def alternate[Case](
        firstAlternative: JavaTrials[_ <: Case],
        secondAlternative: JavaTrials[_ <: Case],
        otherAlternatives: JavaTrials[_ <: Case]*
    ): TrialsImplementation[Case] =
      scalaApi.alternate(
        (firstAlternative +: secondAlternative +: otherAlternatives)
          .map(_.scalaTrials)
      )

    override def alternate[Case](
        alternatives: JavaIterable[JavaTrials[Case]]
    ): TrialsImplementation[Case] =
      scalaApi.alternate(alternatives.asScala.map(_.scalaTrials))

    override def alternate[Case](
        alternatives: Array[JavaTrials[Case]]
    ): TrialsImplementation[Case] =
      scalaApi.alternate(alternatives.toSeq.map(_.scalaTrials))

    override def alternateWithWeights[Case](
        firstAlternative: JavaMap.Entry[JavaInteger, JavaTrials[_ <: Case]],
        secondAlternative: JavaMap.Entry[JavaInteger, JavaTrials[_ <: Case]],
        otherAlternatives: JavaMap.Entry[JavaInteger, JavaTrials[_ <: Case]]*
    ): TrialsImplementation[Case] = scalaApi.alternateWithWeights(
      (firstAlternative +: secondAlternative +: otherAlternatives)
        .map(entry => Int.unbox(entry.getKey) -> entry.getValue.scalaTrials)
    )

    override def alternateWithWeights[Case](
        alternatives: JavaIterable[JavaMap.Entry[JavaInteger, JavaTrials[Case]]]
    ): TrialsImplementation[Case] =
      scalaApi.alternateWithWeights(
        alternatives.asScala.map(entry =>
          Int.unbox(entry.getKey) -> entry.getValue.scalaTrials
        )
      )

    override def alternateWithWeights[Case](
        alternatives: Array[JavaMap.Entry[JavaInteger, JavaTrials[Case]]]
    ): TrialsImplementation[Case] = scalaApi.alternateWithWeights(
      alternatives.toSeq.map(entry =>
        Int.unbox(entry.getKey) -> entry.getValue.scalaTrials
      )
    )

    override def lists[Case](
        listOfTrials: JavaList[JavaTrials[Case]]
    ): TrialsImplementation[ImmutableList[Case]] =
      // NASTY HACK: make a throwaway trials of type `TrialsImplementation` to
      // act as a springboard to flatmap the 'real' result into the correct
      // type.
      scalaApi
        .only(())
        .flatMap((_: Unit) =>
          scalaApi
            .sequences[Case, List](
              listOfTrials.asScala.map(_.scalaTrials).toList
            )
            .map { sequence =>
              val builder = ImmutableList.builder[Case]()
              sequence.foreach(builder.add)
              builder.build()
            }
        )

    override def complexities(): TrialsImplementation[JavaInteger] =
      scalaApi.complexities.map(Int.box)

    override def streamLegacy[Case](
        factory: JavaFunction[JavaLong, Case]
    ): TrialsImplementation[Case] = stream(
      new CaseFactory[Case] {
        override def apply(input: Int): Case   = factory(input)
        override val lowerBoundInput: Int      = Int.MinValue
        override val upperBoundInput: Int      = Int.MaxValue
        override val maximallyShrunkInput: Int = 0
      }
    )

    override def bytes(): JavaTrials[JavaByte] =
      scalaApi.bytes.map(Byte.box)

    override def integers: TrialsImplementation[JavaInteger] =
      scalaApi.integers.map(Int.box)

    override def longs: TrialsImplementation[JavaLong] =
      scalaApi.longs.map(Long.box)

    override def doubles: TrialsImplementation[JavaDouble] =
      scalaApi.doubles.map(Double.box)

    override def booleans: TrialsImplementation[JavaBoolean] =
      scalaApi.booleans.map(Boolean.box)

    override def characters(): TrialsImplementation[JavaCharacter] =
      scalaApi.characters.map(Char.box)

    override def instants(): TrialsImplementation[Instant] =
      scalaApi.instants

    override def strings(): TrialsImplementation[String] =
      scalaApi.strings
  }

  val scalaApi = new CommonApi with TrialsApi {
    override def delay[Case](
        delayed: => Trials[Case]
    ): TrialsImplementation[Case] =
      TrialsImplementation(Free.defer(delayed.generation))

    override def choose[Case](
        choices: Iterable[Case]
    ): TrialsImplementation[Case] =
      new TrialsImplementation(
        Choice(SortedMap.from(LazyList.from(1).zip(choices)))
      )

    override def chooseWithWeights[Case](
        firstChoice: (Int, Case),
        secondChoice: (Int, Case),
        otherChoices: (Int, Case)*
    ): TrialsImplementation[Case] =
      chooseWithWeights(firstChoice +: secondChoice +: otherChoices)

    override def chooseWithWeights[Case](
        choices: Iterable[(Int, Case)]
    ): TrialsImplementation[Case] =
      new TrialsImplementation(
        Choice(choices.unzip match {
          case (weights, plainChoices) =>
            SortedMap.from(
              weights
                .scanLeft(0) {
                  case (cumulativeWeight, weight) if 0 < weight =>
                    cumulativeWeight + weight
                  case (_, weight) =>
                    throw new IllegalArgumentException(
                      s"Weight $weight amongst provided weights of $weights must be greater than zero"
                    )
                }
                .drop(1)
                .zip(plainChoices)
            )
        })
      )

    override def alternate[Case](
        firstAlternative: Trials[Case],
        secondAlternative: Trials[Case],
        otherAlternatives: Trials[Case]*
    ): TrialsImplementation[Case] =
      alternate(
        firstAlternative +: secondAlternative +: otherAlternatives
      )

    override def alternate[Case](
        alternatives: Iterable[Trials[Case]]
    ): TrialsImplementation[Case] =
      choose(alternatives).flatMap(identity[Trials[Case]])

    override def alternateWithWeights[Case](
        firstAlternative: (Int, Trials[Case]),
        secondAlternative: (Int, Trials[Case]),
        otherAlternatives: (Int, Trials[Case])*
    ): TrialsImplementation[Case] = alternateWithWeights(
      firstAlternative +: secondAlternative +: otherAlternatives
    )

    override def alternateWithWeights[Case](
        alternatives: Iterable[
          (Int, Trials[Case])
        ]
    ): TrialsImplementation[Case] =
      chooseWithWeights(alternatives).flatMap(identity[Trials[Case]])

    override def sequences[Case, Sequence[_]: Traverse](
        sequenceOfTrials: Sequence[Trials[Case]]
    )(implicit
        factory: collection.Factory[Case, Sequence[Case]]
    ): Trials[Sequence[Case]] = sequenceOfTrials.sequence

    override def complexities: TrialsImplementation[Int] =
      new TrialsImplementation(NoteComplexity)

    def resetComplexity(complexity: Int): TrialsImplementation[Unit] =
      new TrialsImplementation(ResetComplexity(complexity))

    override def streamLegacy[Case](
        factory: Long => Case
    ): TrialsImplementation[Case] = stream(
      new CaseFactory[Case] {
        override def apply(input: Int): Case   = factory(input)
        override val lowerBoundInput: Int      = Int.MinValue
        override val upperBoundInput: Int      = Int.MaxValue
        override val maximallyShrunkInput: Int = 0
      }
    )

    override def bytes: TrialsImplementation[Byte] =
      streamLegacy(input =>
        (input >> (JavaInteger.SIZE / JavaByte.SIZE - 1)).toByte
      )

    override def integers: TrialsImplementation[Int] =
      streamLegacy(_.toInt)

    // NASTY HACK...
    override def longs: TrialsImplementation[Long] = streamLegacy { input =>
      input match {
        case Int.MinValue => Long.MinValue
        case Int.MaxValue => Long.MaxValue
        case _ =>
          val magnitude = input.abs
          magnitude / Int.MaxValue * Long.MaxValue + (Int.MaxValue - magnitude) * input / Int.MaxValue
      }
    }

    override def doubles: TrialsImplementation[Double] =
      streamLegacy { input =>
        val betweenZeroAndOne = new Random(input).nextDouble()
        Math.scalb(
          betweenZeroAndOne,
          (input.toDouble * JavaDouble.MAX_EXPONENT / Int.MaxValue).toInt
        )
      }
        .flatMap(zeroOrPositive =>
          booleans
            .map((negative: Boolean) =>
              if (negative) -zeroOrPositive else zeroOrPositive
            ): Trials[Double]
        )

    override def booleans: TrialsImplementation[Boolean] =
      choose(true, false)

    override def characters: TrialsImplementation[Char] =
      choose(0 to 0xffff).map((_: Int).toChar)

    override def instants: TrialsImplementation[Instant] =
      longs.map(Instant.ofEpochMilli)

    override def strings: TrialsImplementation[String] = {
      characters.several[String]
    }
  }

  sealed trait Decision

  // Java and Scala API ...

  sealed trait GenerationOperation[Case]

  // Java-only API ...

  private[americium] trait GenerationSupport[+Case] {
    val generation: Generation[_ <: Case]
  }

  // Scala-only API ...

  abstract class CommonApi {

    def only[Case](onlyCase: Case): TrialsImplementation[Case] =
      TrialsImplementation(pure[GenerationOperation, Case](onlyCase))

    def choose[Case](
        firstChoice: Case,
        secondChoice: Case,
        otherChoices: Case*
    ): TrialsImplementation[Case] =
      scalaApi.choose(firstChoice +: secondChoice +: otherChoices)

    def stream[Case](
        caseFactory: CaseFactory[Case]
    ): TrialsImplementation[Case] = new TrialsImplementation(
      Factory(caseFactory)
    )
  }

  case class ChoiceOf(index: Int) extends Decision

  case class FactoryInputOf(input: Long) extends Decision

  // Use a sorted map keyed by cumulative frequency to implement weighted
  // choices. That idea is inspired by Scalacheck's `Gen.frequency`.
  case class Choice[Case](choicesByCumulativeFrequency: SortedMap[Int, Case])
      extends GenerationOperation[Case]

  case class Factory[Case](factory: CaseFactory[Case])
      extends GenerationOperation[Case]

  // NASTY HACK: as `Free` does not support `filter/withFilter`, reify
  // the optional results of a flat-mapped filtration; the interpreter
  // will deal with these.
  case class FiltrationResult[Case](result: Option[Case])
      extends GenerationOperation[Case]

  case object NoteComplexity extends GenerationOperation[Int]

  case class ResetComplexity[Case](complexity: Int)
      extends GenerationOperation[Unit]

  trait Builder[-Case, Container] {
    def +=(caze: Case): Unit

    def result(): Container
  }
}

case class TrialsImplementation[Case](
    override val generation: TrialsImplementation.Generation[_ <: Case]
) extends JavaTrials[Case]
    with Trials[Case] {
  thisTrialsImplementation =>

  import TrialsImplementation._

  override private[americium] val scalaTrials = this

  // Java and Scala API ...
  override def reproduce(recipe: String): Case =
    reproduce(parseDecisionIndices(recipe))

  private def reproduce(decisionStages: DecisionStages): Case = {

    type DecisionIndicesContext[Caze] = State[DecisionStages, Caze]

    // NOTE: unlike the companion interpreter in `cases`,
    // this one has a relatively sane implementation.
    def interpreter: GenerationOperation ~> DecisionIndicesContext =
      new (GenerationOperation ~> DecisionIndicesContext) {
        override def apply[Case](
            generationOperation: GenerationOperation[Case]
        ): DecisionIndicesContext[Case] = {
          generationOperation match {
            case Choice(choicesByCumulativeFrequency) =>
              for {
                decisionStages <- State.get[DecisionStages]
                (ChoiceOf(decisionIndex) :: remainingDecisionStages) =
                  decisionStages
                _ <- State.set(remainingDecisionStages)
              } yield choicesByCumulativeFrequency
                .minAfter(1 + decisionIndex)
                .get
                ._2

            case Factory(factory) =>
              for {
                decisionStages <- State.get[DecisionStages]
                (FactoryInputOf(input) :: remainingDecisionStages) =
                  decisionStages
                _ <- State.set(remainingDecisionStages)
              } yield factory(input.toInt)

            // NOTE: pattern-match only on `Some`, as we are reproducing a case
            // that by dint of being reproduced, must have passed filtration the
            // first time around.
            case FiltrationResult(Some(result)) =>
              result.pure[DecisionIndicesContext]

            case NoteComplexity =>
              0.pure[DecisionIndicesContext]

            case ResetComplexity(_) =>
              ().pure[DecisionIndicesContext]
          }
        }
      }

    generation
      .foldMap(interpreter)
      .runA(decisionStages)
      .value
  }

  private def parseDecisionIndices(recipe: String): DecisionStages = {
    decode[DecisionStages](
      recipe
    ).toTry.get // Just throw the exception, the callers are written in Java style.
  }

  override def withLimit(
      limit: Int
  ): JavaTrials.SupplyToSyntax[Case] with Trials.SupplyToSyntax[Case] =
    withLimit(limit, defaultComplexityWall)

  override def withLimit(
      limit: Int,
      complexityWall: Int
  ): JavaTrials.SupplyToSyntax[Case] with Trials.SupplyToSyntax[Case] =
    new JavaTrials.SupplyToSyntax[Case] with Trials.SupplyToSyntax[Case] {
      final case class NonEmptyDecisionStages(
          latestDecision: Decision,
          previousDecisions: DecisionStagesInReverseOrder
      )

      final case object noDecisionStages extends DecisionStagesInReverseOrder {
        override def appendInReverseOnTo(
            partialResult: DecisionStages
        ): DecisionStages = partialResult
      }

      final case class InternedDecisionStages(index: Int)
          extends DecisionStagesInReverseOrder {
        override def appendInReverseOnTo(
            partialResult: DecisionStages
        ): DecisionStages =
          Option(
            nonEmptyToAndFromInternedDecisionStages.inverse().get(this)
          ) match {
            case Some(
                  NonEmptyDecisionStages(latestDecision, previousDecisions)
                ) =>
              previousDecisions.appendInReverseOnTo(
                latestDecision :: partialResult
              )
          }
      }

      private val nonEmptyToAndFromInternedDecisionStages
          : BiMap[NonEmptyDecisionStages, InternedDecisionStages] =
        HashBiMap.create()

      private def interned(
          nonEmptyDecisionStages: NonEmptyDecisionStages
      ): InternedDecisionStages =
        Option(
          nonEmptyToAndFromInternedDecisionStages.computeIfAbsent(
            nonEmptyDecisionStages,
            _ => {
              val freshIndex = nonEmptyToAndFromInternedDecisionStages.size
              InternedDecisionStages(freshIndex)
            }
          )
        ).get

      sealed trait DecisionStagesInReverseOrder {
        def reverse: DecisionStages = appendInReverseOnTo(List.empty)

        def appendInReverseOnTo(
            partialResult: DecisionStages
        ): DecisionStages

        def addLatest(decision: Decision): DecisionStagesInReverseOrder =
          interned(
            NonEmptyDecisionStages(decision, this)
          )
      }

      private def cases(
          limit: Int,
          complexityWall: Int,
          randomBehaviour: Random,
          scale: BigDecimal
      ): Iterator[(DecisionStagesInReverseOrder, Case)] = {
        require(0 <= scale)

        // This is used instead of a straight `Option[Case]` to avoid stack
        // overflow when interpreting `this.generation`. We need to do this
        // because a) we have to support recursively flat-mapped trials and b)
        // even non-recursive trials can bring in a lot of nested flat-maps. Of
        // course, in the recursive case we merely convert the possibility of
        // infinite recursion into infinite looping through the `Eval`
        // trampolining mechanism, so we still have to guard against that and
        // terminate at some point.
        type DeferredOption[Case] = OptionT[Eval, Case]

        case class State(
            decisionStages: DecisionStagesInReverseOrder,
            complexity: Int
        ) {
          def update(decision: ChoiceOf): State = copy(
            decisionStages = decisionStages.addLatest(decision),
            complexity = 1 + complexity
          )

          def update(decision: FactoryInputOf): State = copy(
            decisionStages = decisionStages.addLatest(decision),
            complexity = 1 + complexity
          )
        }

        object State {
          val initial = new State(
            decisionStages = noDecisionStages,
            complexity = 0
          )
        }

        type StateUpdating[Case] =
          StateT[DeferredOption, State, Case]

        // NASTY HACK: what follows is a hacked alternative to using the reader
        // monad whereby the injected context is *mutable*, but at least it's
        // buried in the interpreter for `GenerationOperation`, expressed as a
        // closure over `randomBehaviour`. The reified `FiltrationResult` values
        // are also handled by the interpreter too. Read 'em and weep!

        sealed trait Possibilities

        case class Choices(possibleIndices: LazyList[Int]) extends Possibilities

        val possibilitiesThatFollowSomeChoiceOfDecisionStages =
          mutable.Map.empty[DecisionStagesInReverseOrder, Possibilities]

        def interpreter(depth: Int): GenerationOperation ~> StateUpdating =
          new (GenerationOperation ~> StateUpdating) {
            override def apply[Case](
                generationOperation: GenerationOperation[Case]
            ): StateUpdating[Case] =
              generationOperation match {
                case Choice(choicesByCumulativeFrequency) =>
                  val numberOfChoices =
                    choicesByCumulativeFrequency.keys.lastOption.getOrElse(0)
                  if (0 < numberOfChoices)
                    for {
                      state <- StateT.get[DeferredOption, State]
                      _ <- liftUnitIfTheNumberOfDecisionStagesIsNotTooLarge(
                        state
                      )
                      index #:: remainingPossibleIndices =
                        possibilitiesThatFollowSomeChoiceOfDecisionStages
                          .get(
                            state.decisionStages
                          ) match {
                          case Some(Choices(possibleIndices))
                              if possibleIndices.nonEmpty =>
                            possibleIndices
                          case _ =>
                            randomBehaviour
                              .buildRandomSequenceOfDistinctIntegersFromZeroToOneLessThan(
                                numberOfChoices
                              )
                        }
                      _ <- StateT.set[DeferredOption, State](
                        state.update(
                          ChoiceOf(index)
                        )
                      )
                    } yield {
                      possibilitiesThatFollowSomeChoiceOfDecisionStages(
                        state.decisionStages
                      ) = Choices(remainingPossibleIndices)
                      choicesByCumulativeFrequency.minAfter(1 + index).get._2
                    }
                  else StateT.liftF(OptionT.none)

                case Factory(factory) =>
                  for {
                    state <- StateT.get[DeferredOption, State]
                    _ <- liftUnitIfTheNumberOfDecisionStagesIsNotTooLarge(state)
                    input = {
                      val upperBoundInput: BigDecimal =
                        factory.upperBoundInput()
                      val lowerBoundInput: BigDecimal =
                        factory.lowerBoundInput()
                      val maximallyShrunkInput: BigDecimal =
                        factory.maximallyShrunkInput()

                      val maximumScale: BigDecimal =
                        (upperBoundInput - lowerBoundInput) / 2

                      // TODO: the calling code needs to understand the scale
                      // dictated by the factory, or perhaps it should just
                      // specify the blend?
                      // For now this only holds by coincidence...
                      require(scale <= maximumScale)

                      val blend: BigDecimal =
                        scale / maximumScale

                      val midPoint: BigDecimal =
                        blend * (upperBoundInput + lowerBoundInput) / 2 + (1 - blend) * maximallyShrunkInput

                      val sign = if (randomBehaviour.nextBoolean()) 1 else -1

                      val delta: BigDecimal =
                        sign * scale * randomBehaviour.nextDouble()

                      (midPoint + delta)
                        .setScale(0, BigDecimal.RoundingMode.HALF_EVEN)
                        .rounded
                        .toLong
                    }
                    _ <- StateT.set[DeferredOption, State](
                      state.update(FactoryInputOf(input))
                    )
                  } yield factory(input.toInt)

                case FiltrationResult(result) =>
                  StateT.liftF(OptionT.fromOption(result))

                case NoteComplexity =>
                  for {
                    state <- StateT.get[DeferredOption, State]
                  } yield state.complexity

                case ResetComplexity(complexity) =>
                  for {
                    _ <- StateT.modify[DeferredOption, State](
                      _.copy(complexity = complexity)
                    )
                  } yield ()
              }

            private def liftUnitIfTheNumberOfDecisionStagesIsNotTooLarge[Case](
                state: State
            ): StateUpdating[Unit] = if (state.complexity < complexityWall)
              StateT.pure(())
            else
              StateT.liftF[DeferredOption, State, Unit](
                OptionT.none
              )
          }

        {
          // NASTY HACK: what was previously a Java-style imperative iterator
          // implementation has, ahem, 'matured' into an overall imperative
          // iterator forwarding to another with a `collect` to flatten out the
          // `Option` part of the latter's output. Both co-operate by sharing
          // mutable state used to determine when the overall iterator should
          // stop yielding output. This in turn allows another hack, namely to
          // intercept calls to `forEach` on the overall iterator so that it can
          // monitor cases that don't pass inline filtration.
          var starvationCountdown: Int         = limit
          var backupOfStarvationCountdown      = 0
          var numberOfUniqueCasesProduced: Int = 0
          val potentialDuplicates =
            mutable.Set.empty[DecisionStagesInReverseOrder]

          def remainingGap = limit - numberOfUniqueCasesProduced

          val coreIterator =
            new Iterator[Option[(DecisionStagesInReverseOrder, Case)]] {
              override def hasNext: Boolean =
                0 < remainingGap && 0 < starvationCountdown

              override def next()
                  : Option[(DecisionStagesInReverseOrder, Case)] =
                generation
                  .foldMap(interpreter(depth = 0))
                  .run(State.initial)
                  .value
                  .value match {
                  case Some((State(decisionStages, _), caze))
                      if potentialDuplicates.add(decisionStages) =>
                    numberOfUniqueCasesProduced += 1
                    backupOfStarvationCountdown = starvationCountdown
                    starvationCountdown = Math
                      .round(Math.sqrt(limit * remainingGap))
                      .toInt
                    Some(decisionStages -> caze)
                  case _ =>
                    starvationCountdown -= 1
                    None
                }
            }.collect { case Some(caze) => caze }

          new Iterator[(DecisionStagesInReverseOrder, Case)] {
            override def hasNext: Boolean = coreIterator.hasNext

            override def next(): (DecisionStagesInReverseOrder, Case) =
              coreIterator.next()

            override def foreach[U](
                f: ((DecisionStagesInReverseOrder, Case)) => U
            ): Unit = {
              super.foreach { input =>
                try {
                  f(input)
                } catch {
                  case _: RejectionByInlineFilter =>
                    numberOfUniqueCasesProduced -= 1
                    starvationCountdown = backupOfStarvationCountdown - 1
                }
              }
            }
          }
        }
      }

      // Java-only API ...
      override def supplyTo(consumer: Consumer[Case]): Unit =
        supplyTo(consumer.accept)

      override def asIterator(): JavaIterator[Case] = {
        val randomBehaviour = new Random(734874)

        val scale: BigDecimal = Int.MaxValue

        cases(limit, complexityWall, randomBehaviour, scale)
          .map(_._2)
          .asJava
      }

      // Scala-only API ...
      override def supplyTo(consumer: Case => Unit): Unit = {

        val randomBehaviour = new Random(734874)

        def shrink(
            caze: Case,
            throwable: Throwable,
            decisionStages: DecisionStages,
            scale: BigDecimal,
            limit: Int
        ): Unit = {
          val numberOfDecisionStages = decisionStages.size

          if (0 < numberOfDecisionStages) {
            // NOTE: there's some voodoo in choosing the exponential scaling
            // factor - if it's too high, say 2, then the solutions are hardly
            // shrunk at all. If it is unity, then the solutions are shrunk a
            // bit but can be still involve overly 'large' values, in the sense
            // that the factory input values are large. This needs finessing,
            // but will do for now...
            val limitWithExtraLeewayThatHasBeenObservedToFindBetterShrunkCases =
              (100 * limit / 99) max limit

            val stillEnoughRoomToDecreaseScale =
              scale >= 1

            cases(
              limitWithExtraLeewayThatHasBeenObservedToFindBetterShrunkCases,
              numberOfDecisionStages,
              randomBehaviour,
              scale
            )
              .foreach {
                case (
                      decisionStagesForPotentialShrunkCaseInReverseOrder,
                      potentialShrunkCase
                    ) =>
                  try {
                    consumer(potentialShrunkCase)
                  } catch {
                    case rejection: RejectionByInlineFilter => throw rejection
                    case throwableFromPotentialShrunkCase: Throwable =>
                      val decisionStagesForPotentialShrunkCase =
                        decisionStagesForPotentialShrunkCaseInReverseOrder.reverse

                      assert(
                        decisionStagesForPotentialShrunkCase.size <= numberOfDecisionStages
                      )

                      val lessComplex =
                        decisionStagesForPotentialShrunkCase.size < numberOfDecisionStages

                      val shouldPersevere =
                        decisionStagesForPotentialShrunkCase.exists {
                          case FactoryInputOf(_)
                              if stillEnoughRoomToDecreaseScale =>
                            true
                          case _ => false
                        } || lessComplex

                      val scaleForRecursion =
                        if (!lessComplex && stillEnoughRoomToDecreaseScale)
                          scale / 2
                        else scale

                      if (shouldPersevere) {
                        shrink(
                          potentialShrunkCase,
                          throwableFromPotentialShrunkCase,
                          decisionStagesForPotentialShrunkCase,
                          scaleForRecursion,
                          limitWithExtraLeewayThatHasBeenObservedToFindBetterShrunkCases
                        )
                      }
                  }
              }

            // At this point, slogging through the potential shrunk cases failed
            // to find any failures; as a brute force approach, simply retry
            // with an increased shrinkage factor - this will eventually
            // terminate as the shrinkage factor isn't allowed to exceed its
            // upper limit, and it does winkle out some really hard to find
            // shrunk cases this way.

            if (stillEnoughRoomToDecreaseScale) {
              val decreasedScale = scale / 2

              shrink(
                caze,
                throwable,
                decisionStages,
                decreasedScale,
                limitWithExtraLeewayThatHasBeenObservedToFindBetterShrunkCases
              )
            }
          }

          // At this point the recursion hasn't found a failing case, so we call
          // it a day and go with the best we've got from further up the call
          // chain...

          throw new TrialException(throwable) {
            override def provokingCase: Case = caze

            override def recipe: String = decisionStages.asJson.spaces4
          }
        }

        val scale: BigDecimal = Int.MaxValue

        cases(limit, complexityWall, randomBehaviour, scale)
          .foreach { case (decisionStagesInReverseOrder, caze) =>
            try {
              consumer(caze)
            } catch {
              case rejection: RejectionByInlineFilter => throw rejection
              case throwable: Throwable =>
                shrink(
                  caze,
                  throwable,
                  decisionStagesInReverseOrder.reverse,
                  scale,
                  limit
                )
            }
          }
      }
    }

  // Java-only API ...
  override def map[TransformedCase](
      transform: JavaFunction[Case, TransformedCase]
  ): TrialsImplementation[TransformedCase] = map(transform.apply)

  override def flatMap[TransformedCase](
      step: JavaFunction[Case, JavaTrials[TransformedCase]]
  ): TrialsImplementation[TransformedCase] =
    flatMap(step.apply _ andThen (_.scalaTrials))

  override def filter(
      predicate: Predicate[Case]
  ): TrialsImplementation[Case] =
    filter(predicate.test)

  def mapFilter[TransformedCase](
      filteringTransform: JavaFunction[Case, Optional[TransformedCase]]
  ): TrialsImplementation[TransformedCase] =
    mapFilter(filteringTransform.apply _ andThen {
      case withPayload if withPayload.isPresent => Some(withPayload.get())
      case _                                    => None
    })

  override def immutableLists(): TrialsImplementation[ImmutableList[Case]] =
    several(new Builder[Case, ImmutableList[Case]] {
      private val underlyingBuilder = ImmutableList.builder[Case]()

      override def +=(caze: Case): Unit = {
        underlyingBuilder.add(caze)
      }

      override def result(): ImmutableList[Case] =
        underlyingBuilder.build()
    })

  override def immutableSets(): TrialsImplementation[ImmutableSet[Case]] =
    several(new Builder[Case, ImmutableSet[Case]] {
      private val underlyingBuilder = ImmutableSet.builder[Case]()

      override def +=(caze: Case): Unit = {
        underlyingBuilder.add(caze)
      }

      override def result(): ImmutableSet[Case] =
        underlyingBuilder.build()
    })

  override def immutableSortedSets(
      elementComparator: JavaComparator[Case]
  ): TrialsImplementation[ImmutableSortedSet[Case]] =
    several(new Builder[Case, ImmutableSortedSet[Case]] {
      private val underlyingBuilder: ImmutableSortedSet.Builder[Case] =
        new ImmutableSortedSet.Builder(elementComparator)

      override def +=(caze: Case): Unit = {
        underlyingBuilder.add(caze)
      }

      override def result(): ImmutableSortedSet[Case] =
        underlyingBuilder.build()
    })

  override def immutableMaps[Value](
      values: JavaTrials[Value]
  ): TrialsImplementation[ImmutableMap[Case, Value]] = {
    flatMap(key => values.map(key -> _))
      .several[Map[Case, Value]]
      .map[JavaMap[Case, Value]](_.asJava)
      .map[ImmutableMap[Case, Value]](ImmutableMap.copyOf(_))
  }
  override def immutableSortedMaps[Value](
      elementComparator: JavaComparator[Case],
      values: JavaTrials[Value]
  ): TrialsImplementation[ImmutableSortedMap[Case, Value]] = {
    flatMap(key => values.map(key -> _))
      .several[Map[Case, Value]]
      .map[JavaMap[Case, Value]](_.asJava)
      .map[ImmutableSortedMap[Case, Value]](
        ImmutableSortedMap.copyOf(_, elementComparator)
      )
  }

  override def immutableListsOfSize(
      size: Int
  ): TrialsImplementation[ImmutableList[Case]] = lotsOfSize(
    size,
    new Builder[Case, ImmutableList[Case]] {
      private val underlyingBuilder = ImmutableList.builder[Case]()

      override def +=(caze: Case): Unit = {
        underlyingBuilder.add(caze)
      }

      override def result(): ImmutableList[Case] =
        underlyingBuilder.build()
    }
  )

  // Scala-only API ...
  override def map[TransformedCase](
      transform: Case => TransformedCase
  ): TrialsImplementation[TransformedCase] =
    TrialsImplementation(generation map transform)

  override def filter(
      predicate: Case => Boolean
  ): TrialsImplementation[Case] = {
    flatMap(caze =>
      new TrialsImplementation(
        FiltrationResult(Some(caze).filter(predicate))
      ): Trials[Case]
    )
  }

  override def mapFilter[TransformedCase](
      filteringTransform: Case => Option[TransformedCase]
  ): TrialsImplementation[TransformedCase] =
    flatMap(caze =>
      new TrialsImplementation(
        FiltrationResult(filteringTransform(caze))
      ): Trials[TransformedCase]
    )

  def this(
      generationOperation: TrialsImplementation.GenerationOperation[Case]
  ) = {
    this(liftF(generationOperation))
  }

  override def flatMap[TransformedCase](
      step: Case => Trials[TransformedCase]
  ): TrialsImplementation[TransformedCase] = {
    val adaptedStep = (step andThen (_.generation))
      .asInstanceOf[Case => Generation[TransformedCase]]
    TrialsImplementation(generation flatMap adaptedStep)
  }

  def withRecipe(
      recipe: String
  ): JavaTrials.SupplyToSyntax[Case] with Trials.SupplyToSyntax[Case] =
    new JavaTrials.SupplyToSyntax[Case] with Trials.SupplyToSyntax[Case] {
      // Java-only API ...
      override def supplyTo(consumer: Consumer[Case]): Unit =
        supplyTo(consumer.accept)

      override def asIterator(): JavaIterator[Case] = Seq {
        val decisionStages = parseDecisionIndices(recipe)
        reproduce(decisionStages)
      }.asJava.iterator()

      // Scala-only API ...
      override def supplyTo(consumer: Case => Unit): Unit = {
        val decisionStages = parseDecisionIndices(recipe)
        val reproducedCase = reproduce(decisionStages)

        try {
          consumer(reproducedCase)
        } catch {
          case exception: Throwable =>
            throw new TrialException(exception) {
              override def provokingCase: Case = reproducedCase

              override def recipe: String = decisionStages.asJson.spaces4
            }
        }
      }
    }

  override def and[Case2](
      secondTrials: JavaTrials[Case2]
  ): JavaTrials.Tuple2Trials[Case, Case2] = {
    new JavaTuple2Trials(this, secondTrials)
  }

  override def and[Case2](
      secondTrials: Trials[Case2]
  ): Trials.Tuple2Trials[Case, Case2] = new Tuple2Trials(this, secondTrials)

  private def several[Container](
      builderFactory: => Builder[Case, Container]
  ): TrialsImplementation[Container] = {
    def addItems(partialResult: List[Case]): TrialsImplementation[Container] =
      scalaApi.alternate(
        scalaApi.only {
          val builder = builderFactory
          partialResult.foreach(builder += _)
          builder.result()
        },
        flatMap(item => addItems(item :: partialResult): Trials[Container])
      )

    addItems(Nil)
  }

  override def several[Container](implicit
      factory: scala.collection.Factory[Case, Container]
  ): TrialsImplementation[Container] = several(new Builder[Case, Container] {
    private val underlyingBuilder = factory.newBuilder

    override def +=(caze: Case): Unit = {
      underlyingBuilder += caze
    }

    override def result(): Container = underlyingBuilder.result()
  })

  override def lists: TrialsImplementation[List[Case]] = several

  override def sets: TrialsImplementation[Set[_ <: Case]] = several

  override def sortedSets(implicit
      ordering: Ordering[_ >: Case]
  ): TrialsImplementation[SortedSet[_ <: Case]] = several(
    new Builder[Case, SortedSet[_ <: Case]] {
      val underlyingBuilder: mutable.Builder[Case, SortedSet[Case]] =
        SortedSet.newBuilder(ordering.asInstanceOf[Ordering[Case]])

      override def +=(caze: Case): Unit = {
        underlyingBuilder += caze
      }

      override def result(): SortedSet[_ <: Case] = underlyingBuilder.result()
    }
  )

  override def maps[Value](
      values: Trials[Value]
  ): TrialsImplementation[Map[Case, Value]] =
    flatMap(key => values.map(key -> _)).several[Map[Case, Value]]

  override def sortedMaps[Value](values: Trials[Value])(implicit
      ordering: Ordering[_ >: Case]
  ): TrialsImplementation[SortedMap[Case, Value]] = {
    flatMap(key => values.map(key -> _)).lists
      .map[SortedMap[Case, Value]](
        SortedMap
          .from[Case, Value](_)(
            ordering.asInstanceOf[Ordering[Case]]
          ): SortedMap[Case, Value]
      )
  }

  private def lotsOfSize[Container](
      size: Int,
      builderFactory: => Builder[Case, Container]
  ): TrialsImplementation[Container] =
    scalaApi.complexities.flatMap(complexity => {
      def addItems(
          numberOfItems: Int,
          partialResult: List[Case]
      ): Trials[Container] =
        if (0 >= numberOfItems)
          scalaApi.only {
            val builder = builderFactory
            partialResult.foreach(builder += _)
            builder.result()
          }
        else
          flatMap(item =>
            (scalaApi
              .resetComplexity(complexity): Trials[Unit])
              .flatMap(_ =>
                addItems(
                  numberOfItems - 1,
                  item :: partialResult
                )
              )
          )

      addItems(size, Nil)
    })

  override def lotsOfSize[Container](size: Int)(implicit
      factory: collection.Factory[Case, Container]
  ): TrialsImplementation[Container] = lotsOfSize(
    size,
    new Builder[Case, Container] {
      private val underlyingBuilder = factory.newBuilder

      override def +=(caze: Case): Unit = {
        underlyingBuilder += caze
      }

      override def result(): Container = underlyingBuilder.result()
    }
  )

  override def listsOfSize(size: Int): Trials[List[Case]] = lotsOfSize(size)
}
