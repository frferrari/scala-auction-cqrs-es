package priceCrawler

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime}

import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.{CodecRegistries, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY

/**
  * Created by Francois FERRARI on 19/06/2017
  */
object MongoCodec {

  private val providers = fromProviders(
    PriceCrawlerItemPrice.mongoCodec,
    PriceCrawlerAuction.mongoCodec,
    PriceCrawlerUrl.mongoCodec,
    PriceCrawlerWebsite.mongoCodec
  )

  def getCodecRegistry: CodecRegistry = fromRegistries( providers, DEFAULT_CODEC_REGISTRY, CodecRegistries.fromCodecs( BigDecimalCodec, InstantCodec, LocalDateCodec, LocalDateTimeCodec ) )

  object BigDecimalCodec extends Codec[BigDecimal] {
    override def decode(reader: BsonReader, decoderContext: DecoderContext): BigDecimal = {
      BigDecimal( reader.readString() )
    }

    override def encode(writer: BsonWriter, value: BigDecimal, encoderContext: EncoderContext): Unit = {
      writer.writeString( value.toString() )
    }

    override def getEncoderClass: Class[BigDecimal] = classOf[BigDecimal]
  }

  object InstantCodec extends Codec[Instant] {
    override def decode(reader: BsonReader, decoderContext: DecoderContext): Instant = {
      Instant.ofEpochMilli( reader.readDateTime() )
    }

    override def encode(writer: BsonWriter, value: Instant, encoderContext: EncoderContext): Unit = {
      writer.writeDateTime( value.toEpochMilli )
    }

    override def getEncoderClass: Class[Instant] = classOf[Instant]
  }

  object LocalDateCodec extends Codec[LocalDate] {
    val ldFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override def decode(reader: BsonReader, decoderContext: DecoderContext): LocalDate = {
      LocalDate.parse( reader.readString(), ldFmt )
    }

    override def encode(writer: BsonWriter, value: LocalDate, encoderContext: EncoderContext): Unit = {
      writer.writeString( ldFmt.format(value) )
    }

    override def getEncoderClass: Class[LocalDate] = classOf[LocalDate]
  }

  object LocalDateTimeCodec extends Codec[LocalDateTime] {
    override def decode(reader: BsonReader, decoderContext: DecoderContext): LocalDateTime = {
      LocalDateTime.parse( reader.readString() )
    }

    override def encode(writer: BsonWriter, value: LocalDateTime, encoderContext: EncoderContext): Unit = {
      writer.writeString( value.toString )
    }

    override def getEncoderClass: Class[LocalDateTime] = classOf[LocalDateTime]
  }
}
