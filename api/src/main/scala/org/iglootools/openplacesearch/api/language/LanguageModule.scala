package org.iglootools.openplacesearch.api.language

import com.google.inject.{Singleton, AbstractModule}

/**
 * @author Sami Dalouche (sami.dalouche@gmail.com)
 */

final class LanguageModule extends AbstractModule {
  def configure: Unit = {
    bind(classOf[LanguageRepository]).to(classOf[InMemoryLanguageRepository]).in(classOf[Singleton])
  }
}