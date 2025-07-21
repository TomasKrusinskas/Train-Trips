//> using scala "3.3.6"
//> using dep "org.scala-lang.modules::scala-xml:2.4.0"
//> using dep "org.knowm.xchart:xchart:3.8.8"

import java.io._
import scala.xml._
import scala.util._
import org.knowm.xchart.{CategoryChartBuilder, BitmapEncoder}

case class Train(version: Int, id: String, seats: Int)
case class Station(version: Int, id: String, name: String)
case class Trip(version: Int, id: String, trainId: String, stationIds: List[String], sourceFile: String)

object XmlTrainsProcessor {
  def main(args: Array[String]): Unit = {
    if args.length != 3 then
      println("Usage: XmlTrainsProcessor <inputDir> <schemaDir-ignored> <outputDir>")
      sys.exit(1)

    val Array(inputDir, _, outputDir) = args
    val in  = new File(inputDir)
    val out = new File(outputDir)
    if !out.exists() then out.mkdirs()

    def safeParseInt(s: String, context: String): Try[Int] = Try(s.toInt).recoverWith {
      case _: NumberFormatException => Failure(new Exception(s"Invalid integer '$s' in $context"))
    }

    val xmlFiles = in.listFiles().filter(_.getName.endsWith(".xml"))
    val parseErrs = xmlFiles.flatMap { f =>
      Try(XML.loadFile(f)) match
        case Failure(e) => 
          val lineInfo = e.getMessage match {
            case msg if msg.contains("line") => msg
            case msg => s"${f.getName}: $msg"
          }
          Some(lineInfo)
        case Success(_) => None
    }
    
    new PrintWriter(s"$outputDir/parse_errors.txt") { 
      write(parseErrs.mkString("\n"))
      close()
    }

    val loaded = xmlFiles.flatMap(f => Try(XML.loadFile(f)).toOption.map(xml => f.getName -> xml))

    val trains: List[Train] = loaded.collect {
      case (n, xml) if n.startsWith("trains") =>
        (xml \\ "train").map { node =>
          for {
            v     <- safeParseInt((node \ "@version").text, s"train version in $n")
            id    = (node \ "id").text
            seats <- safeParseInt((node \ "seats").text, s"train seats in $n")
          } yield Train(v, id, seats)
        }.collect { case Success(train) => train }.toList
    }.flatten.toList

    val stations: List[Station] = loaded.collect {
      case (n, xml) if n.startsWith("stations") =>
        (xml \\ "station").map { node =>
          for {
            v    <- safeParseInt((node \ "@version").text, s"station version in $n")
            id   = if (node \ "@id").text.nonEmpty then (node \ "@id").text else (node \ "id").text
            name = if (node \ "@name").text.nonEmpty then (node \ "@name").text else (node \ "name").text
          } yield Station(v, id, name)
        }.collect { case Success(station) => station }.toList
    }.flatten.toList

    val trips: List[Trip] = loaded.collect {
      case (fname, xml) if fname.startsWith("trips") =>
        (xml \\ "trip").map { node =>
          safeParseInt((node \ "@version").text, s"trip version in $fname") match {
            case Success(v) =>
              val id         = (node \ "@id").text
              val trainId    = (node \ "train").text
              val stationIds = (node \\ "station").map(_.text).toList
              Some(Trip(v, id, trainId, stationIds, fname))
            case Failure(_) => None
          }
        }.collect { case Some(trip) => trip }.toList
    }.flatten.toList

    val trainMap: Map[(Int, String), Int] = trains.groupBy(t => (t.version, t.id)).view.mapValues(_.last.seats).toMap
    val stationMap: Map[(Int, String), String] = stations.groupBy(s => (s.version, s.id)).view.mapValues(_.last.name).toMap

    val (valid, invalid) = trips.partition { t =>
      trainMap.contains((t.version, t.trainId)) &&
        t.stationIds.forall(sid => stationMap.contains((t.version, sid)))
    }

    new PrintWriter(s"$outputDir/invalid_trips.txt") {
      invalid.foreach { t =>
        val missingTrain = if !trainMap.contains((t.version, t.trainId)) then 
          List(s"train ${t.trainId} (version ${t.version})") else List.empty
        val missingStations = t.stationIds
          .filterNot(sid => stationMap.contains((t.version, sid)))
          .map(sid => s"station $sid (version ${t.version})")
        val allMissing = missingTrain ++ missingStations
        write(s"File ${t.sourceFile}, Trip ${t.id}: missing ${allMissing.mkString(", ")}\n")
      }
      close()
    }

    val capacityPairs: List[(String, Int)] = valid.flatMap { t =>
      val seats = trainMap((t.version, t.trainId))
      t.stationIds.map(sid => stationMap((t.version, sid)) -> seats)
    }
    
    val capacity: Map[String, Int] = capacityPairs
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).sum)
      .toMap

    val top15 = capacity.toList.sortBy(-_._2).take(15)
    new PrintWriter(s"$outputDir/top15_stations.txt") {
      top15.foreach { case (name, cap) => write(s"$name: $cap\n") }
      close()
    }

    if capacity.nonEmpty then {
      val chart = new CategoryChartBuilder()
        .width(800)
        .height(600)
        .title("Passenger Capacity per Station")
        .xAxisTitle("Station")
        .yAxisTitle("Capacity")
        .build()

      import scala.jdk.CollectionConverters._
      val sorted = capacity.toSeq.sortBy(_._1)
      val stationNames: java.util.List[String] = sorted.map(_._1).asJava
      val capacityValues: java.util.List[Integer] = sorted.map(_._2.asInstanceOf[Integer]).asJava
      chart.addSeries("Capacity", stationNames, capacityValues)
      BitmapEncoder.saveBitmap(chart, s"$outputDir/capacity_distribution", BitmapEncoder.BitmapFormat.PNG)
    }
    
    println(s"Processed ${xmlFiles.length} XML files")
    println(s"Parse errors: ${parseErrs.length}")
    println(s"Valid trips: ${valid.length}")
    println(s"Invalid trips: ${invalid.length}")
    println(s"Stations with capacity data: ${capacity.size}")
  }
}