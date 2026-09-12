package com.jasperletter.preview.service;

/**
 * Rutas y nombres de archivo del layout fijo del repositorio de recursos
 * (resources/reports/{letterId}/, resources/data/xml/), antes repetidos como literales sueltos
 * en LetterResourceService y DataFileResolver.
 */
final class RepositoryLayout {

    private RepositoryLayout() {
    }

    static final String REPORTS_DIR = "reports";
    static final String DATA_XML_DIR = "data/xml";
    static final String DATA_ADAPTER_FILENAME = "xmlDataAdapter.xml";

    /** Ruta relativa a resourcesDir del directorio de una carta: reports/{letterId} */
    static String letterDirPath(String letterId) {
        return REPORTS_DIR + "/" + letterId;
    }

    /** Ruta relativa a resourcesDir del archivo de datos XML fallback de una carta. */
    static String dataXmlPath(String letterId) {
        return DATA_XML_DIR + "/" + letterId + ".xml";
    }

    /** Ruta de estilo Windows mostrada al usuario para un archivo bajo resources/data/xml/. */
    static String displayDataXmlPath(String fileName) {
        return "resources\\data\\xml\\" + fileName;
    }
}
