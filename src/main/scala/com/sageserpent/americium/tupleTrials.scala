package com.sageserpent.americium

import cats.implicits.*
import com.sageserpent.americium.TrialsScaffolding.ShrinkageStop
import com.sageserpent.americium.java.{CaseSupplyCycle, CasesLimitStrategy}

object tupleTrials {
  // TODO: the same stuff that is on the todo list for the equivalent to this in
  // the Java sub-package.
  class Tuple2Trials[+Case1, +Case2](
      firstTrials: Trials[Case1],
      secondTrials: Trials[Case2]
  ) extends TrialsScaffolding.Tuple2Trials[Case1, Case2] {
    override type SupplySyntaxType = SupplyToSyntaxTuple2

    private def trialsOfPairs: Trials[(Case1, Case2)] =
      (firstTrials, secondTrials).mapN(Tuple2.apply)

    override def trials: Trials[(Case1, Case2)] = trialsOfPairs

    override def reproduce(recipe: String): (Case1, Case2) =
      trialsOfPairs.reproduce(recipe)

    def and[Case3](
        thirdTrials: Trials[Case3]
    ): Tuple3Trials[Case1, Case2, Case3] =
      new Tuple3Trials(firstTrials, secondTrials, thirdTrials)

    trait SupplyToSyntaxTuple2
        extends TrialsScaffolding.SupplyToSyntaxTuple2[Case1, Case2] {
      def supplyTo(consumer: (Case1, Case2) => Unit): Unit = supplyTo(
        consumer.tupled
      )
    }

    def withLimit(limit: Int): SupplyToSyntaxTuple2 = new SupplyToSyntaxTuple2 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2)
      ] = ???

      override def supplyTo(consumer: ((Case1, Case2)) => Unit): Unit =
        trialsOfPairs.withLimit(limit).supplyTo(consumer)
    }

    override def withLimits(
        casesLimit: Int,
        complexityLimit: Int,
        shrinkageAttemptsLimit: Int,
        shrinkageStop: ShrinkageStop[(Case1, Case2)]
    ): SupplyToSyntaxTuple2 = new SupplyToSyntaxTuple2 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2)
      ] = ???

      override def supplyTo(consumer: ((Case1, Case2)) => Unit): Unit =
        trialsOfPairs
          .withLimits(
            casesLimit,
            complexityLimit,
            shrinkageAttemptsLimit,
            shrinkageStop
          )
          .supplyTo(consumer)
    }

    override def withStrategy(
        casesLimitStrategyFactory: CaseSupplyCycle => CasesLimitStrategy,
        complexityLimit: Int,
        shrinkageAttemptsLimit: Int,
        shrinkageStop: ShrinkageStop[
          (Case1, Case2)
        ]
    ): SupplyToSyntaxTuple2 = new SupplyToSyntaxTuple2 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2)
      ] = ???

      override def supplyTo(consumer: ((Case1, Case2)) => Unit): Unit =
        trialsOfPairs
          .withStrategy(
            casesLimitStrategyFactory,
            complexityLimit,
            shrinkageAttemptsLimit,
            shrinkageStop
          )
          .supplyTo(consumer)
    }

    def withRecipe(recipe: String): SupplyToSyntaxTuple2 =
      new SupplyToSyntaxTuple2 {
        override def withSeed(
            seed: Long
        ): TrialsScaffolding.SupplyToSyntax[
          (Case1, Case2)
        ] = ???

        override def withShrinkageStop(
            shrinkageStop: ShrinkageStop[
              (Case1, Case2)
            ]
        ): TrialsScaffolding.SupplyToSyntax[
          (Case1, Case2)
        ] = ???

        override def supplyTo(consumer: ((Case1, Case2)) => Unit): Unit =
          trialsOfPairs.withRecipe(recipe).supplyTo(consumer)
      }
  }

  class Tuple3Trials[+Case1, +Case2, +Case3](
      firstTrials: Trials[Case1],
      secondTrials: Trials[Case2],
      thirdTrials: Trials[Case3]
  ) extends TrialsScaffolding.Tuple3Trials[Case1, Case2, Case3] {
    override type SupplySyntaxType = SupplyToSyntaxTuple3

    private def trialsOfTriples: Trials[(Case1, Case2, Case3)] =
      (firstTrials, secondTrials, thirdTrials).mapN(Tuple3.apply)

    override def trials: Trials[(Case1, Case2, Case3)] = trialsOfTriples

    override def reproduce(
        recipe: String
    ): (Case1, Case2, Case3) = trialsOfTriples.reproduce(recipe)

    def and[Case4](
        fourthTrials: Trials[Case4]
    ): Tuple4Trials[Case1, Case2, Case3, Case4] =
      new Tuple4Trials(firstTrials, secondTrials, thirdTrials, fourthTrials)

    trait SupplyToSyntaxTuple3
        extends TrialsScaffolding.SupplyToSyntaxTuple3[Case1, Case2, Case3] {
      def supplyTo(consumer: (Case1, Case2, Case3) => Unit): Unit = supplyTo(
        consumer.tupled
      )
    }

    def withLimit(limit: Int): SupplyToSyntaxTuple3 = new SupplyToSyntaxTuple3 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3)
      ] = ???

      override def supplyTo(consumer: ((Case1, Case2, Case3)) => Unit): Unit =
        trialsOfTriples.withLimit(limit).supplyTo(consumer)
    }

    override def withLimits(
        casesLimit: Int,
        complexityLimit: Int,
        shrinkageAttemptsLimit: Int,
        shrinkageStop: ShrinkageStop[(Case1, Case2, Case3)]
    ): SupplyToSyntaxTuple3 = new SupplyToSyntaxTuple3 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3)
      ] = ???

      override def supplyTo(consumer: ((Case1, Case2, Case3)) => Unit): Unit =
        trialsOfTriples
          .withLimits(
            casesLimit,
            complexityLimit,
            shrinkageAttemptsLimit,
            shrinkageStop
          )
          .supplyTo(consumer)
    }

    override def withStrategy(
        casesLimitStrategyFactory: CaseSupplyCycle => CasesLimitStrategy,
        complexityLimit: Int,
        shrinkageAttemptsLimit: Int,
        shrinkageStop: ShrinkageStop[
          (Case1, Case2, Case3)
        ]
    ): SupplyToSyntaxTuple3 = new SupplyToSyntaxTuple3 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3)
      ] = ???

      override def supplyTo(consumer: ((Case1, Case2, Case3)) => Unit): Unit =
        trialsOfTriples
          .withStrategy(
            casesLimitStrategyFactory,
            complexityLimit,
            shrinkageAttemptsLimit,
            shrinkageStop
          )
          .supplyTo(consumer)
    }

    def withRecipe(recipe: String): SupplyToSyntaxTuple3 =
      new SupplyToSyntaxTuple3 {
        override def withSeed(
            seed: Long
        ): TrialsScaffolding.SupplyToSyntax[
          (Case1, Case2, Case3)
        ] = ???

        override def withShrinkageStop(
            shrinkageStop: ShrinkageStop[
              (Case1, Case2, Case3)
            ]
        ): TrialsScaffolding.SupplyToSyntax[
          (Case1, Case2, Case3)
        ] = ???

        override def supplyTo(consumer: ((Case1, Case2, Case3)) => Unit): Unit =
          trialsOfTriples.withRecipe(recipe).supplyTo(consumer)
      }
  }

  class Tuple4Trials[+Case1, +Case2, +Case3, +Case4](
      firstTrials: Trials[Case1],
      secondTrials: Trials[Case2],
      thirdTrials: Trials[Case3],
      fourthTrials: Trials[Case4]
  ) extends TrialsScaffolding.Tuple4Trials[Case1, Case2, Case3, Case4] {
    override type SupplySyntaxType = SupplyToSyntaxTuple4

    private def trialsOfQuadruples: Trials[(Case1, Case2, Case3, Case4)] =
      (firstTrials, secondTrials, thirdTrials, fourthTrials).mapN(Tuple4.apply)

    override def trials: Trials[(Case1, Case2, Case3, Case4)] =
      trialsOfQuadruples

    override def reproduce(
        recipe: String
    ): (Case1, Case2, Case3, Case4) = trialsOfQuadruples.reproduce(recipe)

    trait SupplyToSyntaxTuple4
        extends TrialsScaffolding.SupplyToSyntaxTuple4[
          Case1,
          Case2,
          Case3,
          Case4
        ] {
      def supplyTo(consumer: (Case1, Case2, Case3, Case4) => Unit): Unit =
        supplyTo(
          consumer.tupled
        )
    }

    def withLimit(
        limit: Int
    ): SupplyToSyntaxTuple4 = new SupplyToSyntaxTuple4 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3, Case4)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def supplyTo(
          consumer: ((Case1, Case2, Case3, Case4)) => Unit
      ): Unit = trialsOfQuadruples.withLimit(limit).supplyTo(consumer)
    }

    override def withLimits(
        casesLimit: Int,
        complexityLimit: Int,
        shrinkageAttemptsLimit: Int,
        shrinkageStop: ShrinkageStop[(Case1, Case2, Case3, Case4)]
    ): SupplyToSyntaxTuple4 = new SupplyToSyntaxTuple4 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3, Case4)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def supplyTo(
          consumer: ((Case1, Case2, Case3, Case4)) => Unit
      ): Unit = trialsOfQuadruples
        .withLimits(
          casesLimit,
          complexityLimit,
          shrinkageAttemptsLimit,
          shrinkageStop
        )
        .supplyTo(consumer)
    }

    override def withStrategy(
        casesLimitStrategyFactory: CaseSupplyCycle => CasesLimitStrategy,
        complexityLimit: Int,
        shrinkageAttemptsLimit: Int,
        shrinkageStop: ShrinkageStop[
          (Case1, Case2, Case3, Case4)
        ]
    ): SupplyToSyntaxTuple4 = new SupplyToSyntaxTuple4 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3, Case4)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def supplyTo(
          consumer: ((Case1, Case2, Case3, Case4)) => Unit
      ): Unit = trialsOfQuadruples
        .withStrategy(
          casesLimitStrategyFactory,
          complexityLimit,
          shrinkageAttemptsLimit,
          shrinkageStop
        )
        .supplyTo(consumer)
    }

    def withRecipe(
        recipe: String
    ): SupplyToSyntaxTuple4 = new SupplyToSyntaxTuple4 {
      override def withSeed(
          seed: Long
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def withShrinkageStop(
          shrinkageStop: ShrinkageStop[
            (Case1, Case2, Case3, Case4)
          ]
      ): TrialsScaffolding.SupplyToSyntax[
        (Case1, Case2, Case3, Case4)
      ] = ???

      override def supplyTo(
          consumer: ((Case1, Case2, Case3, Case4)) => Unit
      ): Unit = trialsOfQuadruples.withRecipe(recipe).supplyTo(consumer)
    }
  }
}
