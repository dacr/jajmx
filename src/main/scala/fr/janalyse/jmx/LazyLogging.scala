package fr.janalyse.jmx

import org.slf4j._

trait LazyLogging {
  val logger = LoggerFactory.getLogger(getClass)
}
