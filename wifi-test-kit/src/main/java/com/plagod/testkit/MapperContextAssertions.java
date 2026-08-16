package com.plagod.testkit;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MapperContextAssertions {

    private MapperContextAssertions() {
    }

    public static void assertExactMapperBeans(
            String basePackage,
            Class<?>... allowedMappers) {
        Set<Class<?>> allowed = new LinkedHashSet<>(
                Arrays.asList(allowedMappers));
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment(
                "mapper-context-test",
                new JdbcTransactionFactory(),
                new UnpooledDataSource(
                        "org.h2.Driver",
                        "jdbc:h2:mem:mapper-context",
                        null)));

        SqlSessionFactory sqlSessionFactory =
                new DefaultSqlSessionFactory(configuration);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                    "sqlSessionFactory",
                    sqlSessionFactory);
            MapperScannerConfigurer scanner = new MapperScannerConfigurer();
            scanner.setBasePackage(basePackage);
            scanner.setSqlSessionFactoryBeanName("sqlSessionFactory");
            context.addBeanFactoryPostProcessor(scanner);
            context.refresh();

            Set<Class<?>> actual = new LinkedHashSet<>(
                    configuration.getMapperRegistry().getMappers());
            if (!actual.equals(allowed)) {
                throw new AssertionError(
                        "Registered Mapper set differs from allowed set: "
                                + actual);
            }
        }
    }

    public static void assertExactApplicationMapperBeans(
            ApplicationContext context,
            SqlSessionFactory sqlSessionFactory,
            Class<?>... allowedMappers) {
        Set<Class<?>> allowed = new LinkedHashSet<>(
                Arrays.asList(allowedMappers));
        Set<Class<?>> actual = new LinkedHashSet<>(
                sqlSessionFactory.getConfiguration()
                        .getMapperRegistry()
                        .getMappers());
        if (!actual.equals(allowed)) {
            throw new AssertionError(
                    "Application Mapper set differs from allowed set: "
                            + actual);
        }

        for (Class<?> mapper : allowed) {
            if (context.getBeansOfType(mapper).size() != 1) {
                throw new AssertionError(
                        "Expected exactly one Mapper bean for "
                                + mapper.getName());
            }
        }
    }

    public static void assertPackagedMapperXmlStatementsLoaded(
            Resource[] configuredResources,
            String packagedResourcePattern,
            SqlSessionFactory sqlSessionFactory,
            String... expectedStatementIds) throws Exception {
        Resource[] packagedResources =
                new PathMatchingResourcePatternResolver()
                        .getResources(packagedResourcePattern);
        Set<String> configuredNames = resourceNames(configuredResources);
        Set<String> packagedNames = resourceNames(packagedResources);
        if (configuredResources.length != configuredNames.size()
                || packagedResources.length != packagedNames.size()
                || !configuredNames.equals(packagedNames)) {
            throw new AssertionError(
                    "Configured Mapper XML differs from packaged resources: "
                            + "configured=" + configuredNames
                            + ", packaged=" + packagedNames);
        }

        Set<String> actualStatementIds = new LinkedHashSet<>();
        for (Resource resource : packagedResources) {
            actualStatementIds.addAll(
                    assertXmlStatementsLoaded(resource, sqlSessionFactory));
        }
        if (expectedStatementIds.length > 0) {
            Set<String> expected = new LinkedHashSet<>(
                    Arrays.asList(expectedStatementIds));
            if (!actualStatementIds.equals(expected)) {
                throw new AssertionError(
                        "Mapper XML statement set differs from expected: "
                                + "actual=" + actualStatementIds
                                + ", expected=" + expected);
            }
        }
    }

    private static Set<String> resourceNames(Resource[] resources) {
        Set<String> names = new LinkedHashSet<>();
        for (Resource resource : resources) {
            names.add(resource.getFilename());
        }
        return names;
    }

    private static Set<String> assertXmlStatementsLoaded(
            Resource resource,
            SqlSessionFactory sqlSessionFactory) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) ->
                new InputSource(new StringReader("")));
        Document document;
        try (java.io.InputStream input = resource.getInputStream()) {
            document = builder.parse(input);
        }

        Element mapper = document.getDocumentElement();
        String namespace = mapper.getAttribute("namespace");
        if (namespace == null || namespace.trim().isEmpty()) {
            throw new AssertionError(
                    resource.getFilename() + " has no Mapper namespace");
        }

        int statementCount = 0;
        Set<String> statementIds = new LinkedHashSet<>();
        NodeList children = mapper.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element)
                    || !isStatementElement(child.getNodeName())) {
                continue;
            }
            statementCount++;
            String statementId = namespace + "."
                    + ((Element) child).getAttribute("id");
            statementIds.add(statementId);
            Configuration configuration =
                    sqlSessionFactory.getConfiguration();
            if (!configuration.hasStatement(statementId, false)) {
                throw new AssertionError(
                        "Mapper XML statement is not loaded: "
                                + statementId);
            }
            MappedStatement statement =
                    configuration.getMappedStatement(statementId, false);
            if (!statement.getResource().contains(resource.getFilename())) {
                throw new AssertionError(
                        statementId + " loaded from "
                                + statement.getResource());
            }
        }
        if (statementCount == 0) {
            throw new AssertionError(
                    resource.getFilename()
                            + " has no executable Mapper statements");
        }
        return statementIds;
    }

    private static boolean isStatementElement(String name) {
        return "select".equals(name)
                || "insert".equals(name)
                || "update".equals(name)
                || "delete".equals(name);
    }
}
