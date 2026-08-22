package com.privateflow.modules.tablewrite.callback;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class XmlValues {

  private final Map<String, List<String>> values;

  private XmlValues(Map<String, List<String>> values) {
    this.values = values;
  }

  static XmlValues parse(String xml) {
    if (xml == null || xml.isBlank()) {
      throw new IllegalArgumentException("WeCom callback XML was empty");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      Element root = document.getDocumentElement();
      if (root == null || !"xml".equals(root.getTagName())) {
        throw new IllegalArgumentException("WeCom callback XML root was invalid");
      }
      Map<String, List<String>> values = new LinkedHashMap<>();
      NodeList children = root.getChildNodes();
      for (int index = 0; index < children.getLength(); index++) {
        Node child = children.item(index);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          values.computeIfAbsent(child.getNodeName(), ignored -> new ArrayList<>())
              .add(child.getTextContent() == null ? "" : child.getTextContent().trim());
        }
      }
      return new XmlValues(values);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("WeCom callback XML was invalid");
    }
  }

  String first(String name) {
    List<String> found = values.get(name);
    if (found == null || found.isEmpty() || found.get(0).isBlank()) {
      throw new IllegalArgumentException("WeCom callback XML was missing " + name);
    }
    return found.get(0);
  }

  List<String> all(String name) {
    return List.copyOf(values.getOrDefault(name, List.of()));
  }
}
