package org.iglootools.openplacesearch.api.gisgraphy.resultparser

import java.io.{InputStream}
import xml.{Node, XML}
import org.iglootools.openplacesearch.api.administrativedivision.{AdministrativeDivisionRepository, CountryRepository, Place}
import org.iglootools.openplacesearch.api.language.LanguageRepository
import org.joda.time.DateTimeZone
import org.iglootools.openplacesearch.api.feature._
import org.iglootools.commons.scala.jts.JtsPoint
import org.iglootools.openplacesearch.api.StableIDMapper
import org.iglootools.openplacesearch.api.geonames._

/**
 *
 * <p>
 * Does not close the inputStream !!
 * </p>
 * @author Sami Dalouche (sami.dalouche@gmail.com)
 */
protected[gisgraphy] final class FullTextResultParser(implicit private[this] val countryRepository: CountryRepository,
                                 implicit private[this] val administrativeDivisionRepository: AdministrativeDivisionRepository,
                                 implicit private[this] val languageRepository: LanguageRepository,
                                 implicit private[this] val stableIDMapper: StableIDMapper) extends ResultParser {

  def toPlaces(inputStream: InputStream): List[Place] = {
    (XML.load(inputStream) \ "result" \ "doc").map { result => toPlace(result)}.toList
  }

  private def toPlace(root: Node) = {
    val countryCode = value(root, "str", "country_code")
    val adm1Code = value(root, "str", "adm1_code")
    val adm2Code = option(root, "str", "adm2_code")
    val adm3Code = option(root, "str", "adm3_code") // + alternate names ?
    val adm4Code = option(root, "str", "adm4_code") // + alternate names ?
    val featureClass = value(root, "str", "feature_class")
    val featureCode = value(root, "str", "feature_code")
    val featureId = value(root, "long", "feature_id").toLong
    val latitude = value(root, "double", "lat").toDouble
    val longitude = value(root, "double", "lng").toDouble
    val name = value(root, "str", "name")
    val gtopo30 = option(root,"int", "gtopo30").map(_.toLong)
    val elevation = option(root,"int", "elevation").map(_.toLong)
    val population = option(root, "int", "population").map(_.toLong)
    val timezone = option(root, "str", "timezone").map {s => DateTimeZone.forID(s)}

    val country = countryRepository.getByIsoAlpha2Code(countryCode)
    val adm1 = administrativeDivisionRepository.getFirstOrderAdministrativeDivision(country, adm1Code)
    val adm2 = adm2Code.map { c => administrativeDivisionRepository.getSecondOrderAdministrativeDivision(country, adm1, c)}
    // FIXME: adm3+4 ?

    GeonamesPlace(
      GisFeature(
        featureGeography=FeatureGeography(
          location=JtsPoint(longitude,latitude),
          population=population,
          gTopo30ElevationInMeters=gtopo30,
          elevationInMeters=elevation,
          timeZone=timezone),
        geonamesFeature=GeonamesFeature(geonamesId=featureId, geonamesFeatureCategory = GeonamesFeatureCategory(featureClass=featureClass, featureCode=featureCode)),
        featureNames=FeatureNames(defaultName=name, localizedNamesSupplier = new InMemoryLocalizedNamesSupplier(alternateNames(root))),
        parentAdministrativeEntity=adm2.orElse(Some(adm1))))
  }

  private def value(root: Node, element: String, name: String):String = {
    option(root, element, name).get
  }

  private def option(root: Node, element: String, name: String):Option[String] = {
    (root \ element).find{n => (n \ "@name").text == name }.map(_.text)
  }

  private def alternateNames(root: Node):Iterable[LocalizedName] = {
    (root \ "arr").filter{n => (n \ "@name").text.startsWith("name_alternate_")}.flatMap{n =>
      val language = (n \ "@name").text.stripPrefix("name_alternate_").toLowerCase match {
        case alpha2 if alpha2.size == 2 => Some(languageRepository.getByAlpha2Code(alpha2))
        case alpha3 if alpha3.size == 3 =>
          // we do not support the weird family language codes, so we are just going to ignore these languages for now
          languageRepository.maybeGetByAlpha3Code(alpha3)
        case _ => // we ignore all alternate names that are not ISO languages (airport codes, etc)
          None
      }
      language.map {l => new LocalizedName(name=((n \ "str").text), language=l)}
    }
  }
}