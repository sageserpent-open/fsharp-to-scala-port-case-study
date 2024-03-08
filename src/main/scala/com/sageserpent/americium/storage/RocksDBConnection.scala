package com.sageserpent.americium.storage
import cats.Eval
import com.google.common.collect.ImmutableList
import com.sageserpent.americium.generation.JavaPropertyNames.{runDatabaseJavaProperty, temporaryDirectoryJavaProperty}
import com.sageserpent.americium.generation.SupplyToSyntaxSkeletalImplementation.runDatabaseDefault
import org.rocksdb.*

import _root_.java.util.ArrayList as JavaArrayList
import java.nio.file.Path
import scala.util.Using

object RocksDBConnection {
  private def runDatabasePath: Path =
    Option(System.getProperty(temporaryDirectoryJavaProperty)) match {
      case None =>
        throw new RuntimeException(
          s"No definition of Java property: `$temporaryDirectoryJavaProperty`"
        )

      case Some(directory) =>
        val file = Option(
          System.getProperty(runDatabaseJavaProperty)
        ).getOrElse(runDatabaseDefault)

        Path
          .of(directory)
          .resolve(file)
    }

  private val rocksDbOptions = new DBOptions()
    .optimizeForSmallDb()
    .setCreateIfMissing(true)
    .setCreateMissingColumnFamilies(true)

  private val columnFamilyOptions = new ColumnFamilyOptions()
    .setCompressionType(CompressionType.LZ4_COMPRESSION)
    .setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION)

  private val defaultColumnFamilyDescriptor = new ColumnFamilyDescriptor(
    RocksDB.DEFAULT_COLUMN_FAMILY,
    columnFamilyOptions
  )

  private val columnFamilyDescriptorForRecipeHashes =
    new ColumnFamilyDescriptor(
      "RecipeHashKeyRecipeValue".getBytes(),
      columnFamilyOptions
    )

  private val columnFamilyDescriptorForTestCaseIds = new ColumnFamilyDescriptor(
    "TestCaseIdKeyRecipeValue".getBytes(),
    columnFamilyOptions
  )

  private def connection(readOnly: Boolean): RocksDBConnection = {
    val columnFamilyDescriptors =
      ImmutableList.of(
        defaultColumnFamilyDescriptor,
        columnFamilyDescriptorForRecipeHashes,
        columnFamilyDescriptorForTestCaseIds
      )

    val columnFamilyHandles = new JavaArrayList[ColumnFamilyHandle]()

    val rocksDB =
      if (readOnly)
        RocksDB.openReadOnly(
          rocksDbOptions,
          runDatabasePath.toString,
          columnFamilyDescriptors,
          columnFamilyHandles
        )
      else
        RocksDB.open(
          rocksDbOptions,
          runDatabasePath.toString,
          columnFamilyDescriptors,
          columnFamilyHandles
        )

    RocksDBConnection(
      rocksDB,
      columnFamilyHandleForRecipeHashes = columnFamilyHandles.get(1),
      columnFamilyHandleForTestCaseIds = columnFamilyHandles.get(2)
    )
  }

  def readOnlyConnection(): RocksDBConnection = connection(readOnly = true)

  val evaluation: Eval[RocksDBConnection] =
    Eval.later {
      val result = connection(readOnly = false)
      Runtime.getRuntime.addShutdownHook(new Thread(() => result.close()))
      result
    }
}

// TODO: split the responsibilities into two databases? `SupplyToSyntaxSkeletalImplementation` cares about recipe
// hashes and is core functionality, whereas `TrialsTestExtension` cares about test case ids and is an add-on.
case class RocksDBConnection(
    rocksDb: RocksDB,
    columnFamilyHandleForRecipeHashes: ColumnFamilyHandle,
    columnFamilyHandleForTestCaseIds: ColumnFamilyHandle
) {
  def reset(): Unit = {
    Using.resource(rocksDb.newIterator(columnFamilyHandleForRecipeHashes)) {
      iterator =>

        val firstRecipeHash: Array[Byte] = {
          iterator.seekToFirst()
          iterator.key
        }

        val onePastLastRecipeHash: Array[Byte] = {
          iterator.seekToLast()
          iterator.key() :+ 0
        }

        // NOTE: the range has an exclusive upper bound, hence the use of
        // `onePastLastRecipeHash`.
        rocksDb.deleteRange(
          columnFamilyHandleForRecipeHashes,
          firstRecipeHash,
          onePastLastRecipeHash
        )
    }
  }

  def recordRecipeHash(recipeHash: String, recipe: String): Unit = {
    rocksDb.put(
      columnFamilyHandleForRecipeHashes,
      recipeHash.map(_.toByte).toArray,
      recipe.map(_.toByte).toArray
    )
  }

  // TODO: suppose there isn't a recipe? This should look like
  // `recipeFromTestCaseId`...
  def recipeFromRecipeHash(recipeHash: String): String = rocksDb
    .get(
      columnFamilyHandleForRecipeHashes,
      recipeHash.map(_.toByte).toArray
    )
    .map(_.toChar)
    .mkString

  def recordTestCaseId(testCaseId: String, recipe: String): Unit = {
    rocksDb.put(
      columnFamilyHandleForTestCaseIds,
      testCaseId.map(_.toByte).toArray,
      recipe.map(_.toByte).toArray
    )
  }

  def recipeFromTestCaseId(testCaseId: String): Option[String] = Option(
    rocksDb
      .get(
        columnFamilyHandleForTestCaseIds,
        testCaseId.map(_.toByte).toArray
      )
  ).map(_.map(_.toChar).mkString)

  def close(): Unit = {
    columnFamilyHandleForRecipeHashes.close()
    columnFamilyHandleForTestCaseIds.close()
    rocksDb.close()
  }
}
