/*
    Open Aviation Map
    Copyright (C) 2012 Ákos Maróy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package hu.tyrell.openaviationmap.rendering;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ws.commons.util.NamespaceContextImpl;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Utility program to pre-process SLD files, so that they are scaled
 * for a particular DPI value, and a number of map scales.
 */
public final class ScaleSLD {

    /** Namespace prefix for the SLD namespace. */
    private static final String SLD_NS_PREFIX = "sld";

    /** Namespace URI for the SLD namespace. */
    private static final String SLD_NS_URI    = "http://www.opengis.net/sld";

    /** Namespace prefix for the OGC namespace. */
    private static final String OGC_NS_PREFIX = "ogc";

    /** Namespace URI for the OGC namespace. */
    private static final String OGC_NS_URI    = "http://www.opengis.net/ogc";

    /** Namespace prefix for the GML namespace. */
    private static final String GML_NS_PREFIX = "gml";

    /** Namespace URI for the GML namespace. */
    private static final String GML_NS_URI    = "http://www.opengis.net/gml";

    /** Namespace prefix for the XLink namespace. */
    private static final String XLINK_NS_PREFIX = "xlink";

    /** Namespace URI for the XLink namespace. */
    private static final String XLINK_NS_URI = "http://www.w3.org/1999/xlink";

    /**
     * Private default constructor.
     */
    private ScaleSLD() {
    }

    /**
     * Print a help message.
     */
    private static void printHelpMessage() {
        System.out.println(
            "Open Aviation Map SLD scaling utility");
        System.out.println();
        System.out.println(
            "usage:");
        System.out.println();
        System.out.println(
            "  -i | --input <input.file>    specify the input file, required");
        System.out.println(
            "  -o | --output <output.file>  the output file");
        System.out.println(
            "  -s | --scales N,M,...        a comma-separated list of scales");
        System.out.println(
            "                               to generate segments for in the");
        System.out.println(
            "                               output file, e.g. 50000,100000");
        System.out.println(
            "  -h | --help                  show this usage page");
        System.out.println();
    }

    /**
     * Program entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {

        LongOpt[] longopts = new LongOpt[4];

        longopts[0] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
        longopts[1] = new LongOpt("input", LongOpt.REQUIRED_ARGUMENT,
                null, 'i');
        longopts[2] = new LongOpt("output", LongOpt.REQUIRED_ARGUMENT,
                null, 'o');
        longopts[3] = new LongOpt("scales", LongOpt.REQUIRED_ARGUMENT,
                null, 's');

        Getopt g = new Getopt("ScaleSLD", args, "hi:o:s:", longopts);

        int c;

        String      inputFile   = null;
        String      outputFile  = null;
        String      strScales   = null;

        while ((c = g.getopt()) != -1) {
            switch (c) {
            case 'i':
                inputFile = g.getOptarg();
                break;

            case 'o':
                outputFile = g.getOptarg();
                break;

            case 's':
                strScales = g.getOptarg();
                break;

            default:
            case 'h':
                printHelpMessage();
                return;

            case '?':
                System.out.println("Invalid option '" + g.getOptopt()
                                   + "' specified");
                return;
            }
        }

        if (inputFile == null) {
            System.out.println("Required option input not specified");
            System.out.println();
            printHelpMessage();
            return;
        }
        if (outputFile == null) {
            System.out.println("Required option output not specified");
            System.out.println();
            printHelpMessage();
            return;
        }
        if (strScales == null) {
            System.out.println("Required option scales not specified");
            System.out.println();
            printHelpMessage();
            return;
        }

        // parse the scales
        StringTokenizer tok    = new StringTokenizer(strScales, ",");
        List<Integer>   scales = new ArrayList<Integer>(tok.countTokens());
        try {
            while (tok.hasMoreTokens()) {
                scales.add(Integer.parseInt(tok.nextToken()));
            }
        } catch (Exception e) {
            System.out.println("Error parsing supplied scale values.");
            System.out.println();
            e.printStackTrace(System.out);
            return;
        }

        try {
            FileReader   reader = new FileReader(inputFile);
            Writer       writer = new FileWriter(outputFile);

            scaleSld(reader, scales, writer);

        } catch (Exception e) {
            System.out.println("Scaling failed.");
            System.out.println();
            e.printStackTrace(System.out);
            return;
        }

        System.out.println("Scaling successful.");
    }

    /**
     * Return a namespace context with the used namespaces and their
     * preferred prefixes.
     *
     * @return a namespace context with namespaces used by SLD documents.
     */
    public static NamespaceContext getNsCtx() {
        NamespaceContextImpl nsCtx = new NamespaceContextImpl();

        nsCtx.startPrefixMapping(SLD_NS_PREFIX, SLD_NS_URI);
        nsCtx.startPrefixMapping(OGC_NS_PREFIX, OGC_NS_URI);
        nsCtx.startPrefixMapping(GML_NS_PREFIX, GML_NS_URI);
        nsCtx.startPrefixMapping(XLINK_NS_PREFIX, XLINK_NS_URI);

        return nsCtx;
    }

    /**
     * Scale an SLD file.
     *
     * @param input the input file to scale
     * @param scales a list of scales, interpreted as 1:N, for which to generate
     *        different segments in the generated SLD output
     * @param output generate the scaled result into this output file
     * @throws ParserConfigurationException on XML parser configuration errors
     * @throws SAXException on XML parser errors
     * @throws IOException on I/O errors
     * @throws TransformerException on XML transformer errors
     * @throws XPathExpressionException on XPath errors
     * @throws RenderException on SLD scaling, rendering issues
     */
    static void
    scaleSld(Reader         input,
             List<Integer>  scales,
             Writer         output)
                                        throws ParserConfigurationException,
                                               SAXException,
                                               IOException,
                                               TransformerException,
                                               XPathExpressionException,
                                               RenderException {
        // read the input file
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder        db  = dbf.newDocumentBuilder();

        InputSource  fSource = new InputSource(input);
        Document     d = db.parse(fSource);

        // scale the loaded SLD
        Document dd = scaleSld(d, scales);

        // write the XML document into a file
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        DOMSource source = new DOMSource(dd);
        StreamResult result = new StreamResult(output);
        transformer.transform(source, result);
    }

    /**
     * Scale an SLD document.
     *
     * @param input the input document to scale
     * @param scales a list of scales, interpreted as 1:N, for which to generate
     *        different segments in the generated SLD output
     * @return the scaled result as an XML document
     * @throws XPathExpressionException on XPath errors
     * @throws RenderException on SLD parsing, scaling errors
     * @throws ParserConfigurationException on XML parser config errors
     */
    static Document scaleSld(Document        input,
                             List<Integer>   scales)
                                      throws XPathExpressionException,
                                             RenderException,
                                             ParserConfigurationException {

        // check if this is really an SLD document
        Node root = input.getDocumentElement();
        if (!"StyledLayerDescriptor".equals(root.getLocalName())
         || !"http://www.opengis.net/sld".equals(root.getNamespaceURI())) {

            throw new RenderException("input document is not an SLD document");
        }

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(getNsCtx());

        // see if this document already contains scaling information
        Double d = (Double) xpath.evaluate(
                "count(//sld:MinScaleDenominator|//sld:MaxScaleDenominator)",
                input, XPathConstants.NUMBER);
        if (d != 0) {
            throw new RenderException("document is already scaled, "
                                    + "rescaling not supported");
        }

        // duplicate the input at first
        DocumentBuilderFactory dbf    = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder        db     = dbf.newDocumentBuilder();
        Document               output = db.newDocument();

        Node r = output.importNode(root, true);
        output.appendChild(r);

        // now add scales, if any
        if (scales.size() == 1) {
            scaleSldSingle(output, scales.get(0));
        } else if (scales.size() > 1) {
            scaleSldMultiple(output, scales);
        }

        return output;
    }

    /**
     * Scale with a single scaling value, that is, split the scaling into two
     * based on this value. The result will be two sld:Rule elements created
     * for each sld:Rule, which is split by scale denominators at the supplied
     * value.
     *
     * @param document the SLD document to scale.
     * @param scale the single scale point to split rendering by
     * @throws XPathExpressionException on XPath errors
     */
    private static void
    scaleSldSingle(Document document,
                   int      scale) throws XPathExpressionException {

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(getNsCtx());

        NodeList rules = (NodeList) xpath.evaluate("//sld:Rule",
                                                   document,
                                                   XPathConstants.NODESET);

        for (int i = 0; i < rules.getLength(); ++i) {
            Element rule = (Element) rules.item(i);

            // create a copy of this rule element
            Element ruleCopy = (Element) rule.cloneNode(true);

            // insert the min and max scale denominators
            insertMinScaleDenominator(rule, scale);
            insertMaxScaleDenominator(ruleCopy, scale);

            // insert the copied rule before the original one
            rule.getParentNode().insertBefore(ruleCopy, rule);
        }
    }

    /**
     * Scale with multiple scaling values.
     * The result will be scales.size() + 1 sld:Rule elements created
     * for each sld:Rule, with proper scale denominators added.
     *
     * @param document the SLD document to scale.
     * @param scales the scaling points
     * @throws XPathExpressionException on XPath errors
     */
    private static void
    scaleSldMultiple(Document       document,
                     List<Integer>  scales) throws XPathExpressionException {

        // don't solve this here if there are less than 2 scaling values
        if (scales.isEmpty()) {
            return;
        } else if (scales.size() == 1) {
            scaleSldSingle(document, scales.get(0));
            return;
        }

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(getNsCtx());

        NodeList rules = (NodeList) xpath.evaluate("//sld:Rule",
                                                   document,
                                                   XPathConstants.NODESET);

        for (int i = 0; i < rules.getLength(); ++i) {
            Element          rule = (Element) rules.item(i);
            DocumentFragment df   = document.createDocumentFragment();

            // the first duplicate will have a single max scale denominator
            Element ruleCopy = (Element) rule.cloneNode(true);
            int     sMax     = scales.get(0);
            int     nScales1 = scales.size() - 1;
            insertMaxScaleDenominator(ruleCopy, sMax);
            df.appendChild(ruleCopy);

            // process the intermediate rules, that have box max and min scale
            // denominators
            for (int j = 1; j <= nScales1; ++j) {
                int sMin = sMax;
                sMax     = scales.get(j);
                ruleCopy = (Element) rule.cloneNode(true);

                // insert the min and max scale denominators
                insertMaxScaleDenominator(ruleCopy, sMax);
                insertMinScaleDenominator(ruleCopy, sMin);

                df.appendChild(ruleCopy);
            }

            // update the original rule with a min scale denominator
            insertMinScaleDenominator(rule, scales.get(nScales1));

            // insert the new rules before the original one
            rule.getParentNode().insertBefore(df, rule);
        }
    }

    /**
     * Insert an sdl:MinScaleDenominator element into an sld:Rule element.
     *
     * @param rule the sld:Rule element to insert into
     * @param scale the scale value of the scale denominator element.
     * @throws XPathExpressionException on XPath errors
     */
    private static void
    insertMinScaleDenominator(Element rule, int scale)
                                            throws XPathExpressionException {

        insertScaleDenominator(rule, scale,
                               SLD_NS_PREFIX + ":MinScaleDenominator");
    }

    /**
     * Insert an sdl:MaxScaleDenominator element into an sld:Rule element.
     *
     * @param rule the sld:Rule element to insert into
     * @param scale the scale value of the scale denominator element.
     * @throws XPathExpressionException on XPath errors
     */
    private static void
    insertMaxScaleDenominator(Element rule, int scale)
                                            throws XPathExpressionException {

        insertScaleDenominator(rule, scale,
                               SLD_NS_PREFIX + ":MaxScaleDenominator");
    }

    /**
     * Insert a scale denominator element of the specified name.
     *
     * @param rule the sld:Rule element to insert into
     * @param scale the scale value of the scale denominator element.
     * @param elementName the name of the scale denominator element
     * @throws XPathExpressionException on XPath errors
     */
    private static void
    insertScaleDenominator(Element rule, int scale, String elementName)
                                            throws XPathExpressionException {

        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(getNsCtx());

        // create the scale denominator element
        Document d   = rule.getOwnerDocument();
        Element  msd = d.createElementNS(SLD_NS_URI, elementName);
        msd.setTextContent(Integer.toString(scale));

        // insert the scale denominator into its proper place
        Node n = (Node) xpath.evaluate("sld:Name", rule, XPathConstants.NODE);
        if (n != null) {
            rule.insertBefore(msd, n.getNextSibling());
        } else {
            rule.insertBefore(msd, rule.getFirstChild());
        }
    }
}
