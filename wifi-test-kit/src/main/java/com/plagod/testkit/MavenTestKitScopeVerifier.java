package com.plagod.testkit;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class MavenTestKitScopeVerifier {

    private MavenTestKitScopeVerifier() {
    }

    public static void assertOnlyTestScoped(Path repositoryRoot) {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot, 4)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(path -> inspectPom(path, violations));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot inspect Maven test-kit scopes");
        }

        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "wifi-test-kit must use test scope: " + String.join(",", violations));
        }
    }

    private static void inspectPom(Path pom, List<String> violations) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Document document = factory.newDocumentBuilder().parse(pom.toFile());
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int index = 0; index < dependencies.getLength(); index++) {
                Element dependency = (Element) dependencies.item(index);
                if (!"wifi-test-kit".equals(childText(dependency, "artifactId"))) {
                    continue;
                }
                if (!"test".equals(childText(dependency, "scope"))) {
                    violations.add(pom.toString());
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("cannot parse Maven descriptor");
        }
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && name.equals(child.getLocalName() == null
                    ? child.getNodeName()
                    : child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
