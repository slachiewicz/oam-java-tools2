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
package hu.tyrell.openaviationmap.converter;

import hu.tyrell.openaviationmap.model.Airspace;
import hu.tyrell.openaviationmap.model.Boundary;
import hu.tyrell.openaviationmap.model.Circle;
import hu.tyrell.openaviationmap.model.Distance;
import hu.tyrell.openaviationmap.model.Elevation;
import hu.tyrell.openaviationmap.model.ElevationReference;
import hu.tyrell.openaviationmap.model.Point;
import hu.tyrell.openaviationmap.model.Ring;
import hu.tyrell.openaviationmap.model.UOM;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A class to read eAIP publications published for Hungary.
 */
public class EAIPHungaryReader {
    /**
     * The prefix string for a circle description.
     */
    private static final String CIRCLE_PREFIX = "A circle radius";

    /**
     * The infix string for a circle description, between radius an center
     * point. note: it's not a typo, this is how it is in the document
     */
    private static final String CIRCLE_INFIX = "centred on";

    /**
     * Convert a latitude string into a latitude value.
     *
     * @param latStr the latitude string
     * @return the latitude value
     */
    private double processLat(String latStr) {
        double degrees = Double.parseDouble(latStr.substring(0, 2));
        double minutes = Double.parseDouble(latStr.substring(2, 4));
        double seconds = Double.parseDouble(latStr.substring(4, 6));

        double value = degrees + (minutes / 60.0) + (seconds / 3600.0);

        return latStr.charAt(6) == 'S' ? -value : value;
    }

    /**
     * Convert a longitude string into a longitude value.
     *
     * @param lonStr the longitude string
     * @return the longitude value
     */
    private double processLon(String lonStr) {
        double degrees = Double.parseDouble(lonStr.substring(0, 3));
        double minutes = Double.parseDouble(lonStr.substring(3, 5));
        double seconds = Double.parseDouble(lonStr.substring(5, 7));

        double value = degrees + (minutes / 60.0) + (seconds / 3600.0);

        return lonStr.charAt(7) == 'W' ? -value : value;
    }

    /**
     * Convert an eAIP point string description into a Point object.
     *
     * @param pointDesc a textual point description
     * @return the corresponding Point object.
     */
    private Point processPoint(String pointDesc) {
        String pd = pointDesc.trim();
        int space = pd.indexOf(" ");
        String latStr = pd.substring(0, space).trim();
        String lonStr = pd.substring(space + 1).trim();

        Point point = new Point();
        point.setLatitude(processLat(latStr));
        point.setLongitude(processLon(lonStr));

        return point;
    }

    /**
     * Parse an eAIP point list into a Boundary.
     *
     * @param designator the airspace designator for the point list,
     *                   used to display warnings about the incompleteness
     *                   of the airspace.
     * @param boundaryDesc the textual boundary description
     * @return the airspace boundary
     */
    private Ring processPointList(String designator, String boundaryDesc) {
        ArrayList<Point> pointList = new ArrayList<Point>();

        int ix       = 0;
        int borderIx = boundaryDesc.indexOf("along border", ix);

        while (ix != -1 && borderIx != -1) {
            StringTokenizer tokenizer =
                    new StringTokenizer(boundaryDesc.substring(ix, borderIx),
                                        "-");
            while (tokenizer.hasMoreTokens()) {
                String str = tokenizer.nextToken();
                pointList.add(processPoint(str));
            }

            ix       = boundaryDesc.indexOf("-", borderIx);
            System.out.println("WARINING: airspace " + designator
                             + " contains the following country border: "
                             + boundaryDesc.substring(borderIx, ix));
            borderIx = boundaryDesc.indexOf("along border", ix);
        }

        StringTokenizer tokenizer =
                new StringTokenizer(boundaryDesc.substring(ix), "-");
        while (tokenizer.hasMoreTokens()) {
            String str = tokenizer.nextToken();
            pointList.add(processPoint(str));
        }

        Ring boundary = new Ring();
        boundary.setPointList(pointList);

        return boundary;
    }

    /**
     * Process a textual elevation description.
     *
     * @param elevDesc the textual elevation description
     * @return the elevation described by elevDesc
     */
    private Elevation processElevation(String elevDesc) {
        String ed = elevDesc.trim();

        Elevation elevation = new Elevation();

        if ("GND".equals(ed)) {
            elevation.setElevation(0);
            elevation.setReference(ElevationReference.SFC);
            elevation.setUom(UOM.FT);
        } else if (ed.startsWith("FL")) {
            elevation.setElevation(
                    Double.parseDouble(ed.substring(2).trim()));
            elevation.setReference(ElevationReference.MSL);
            elevation.setUom(UOM.FL);
        } else {
            // get the elevation
            int i = ed.indexOf(" ");
            elevation.setElevation(
                    Double.parseDouble(ed.substring(0, i)));

            // get the unit of measurement
            int j = ed.indexOf(" ", i + 1);
            String uom = ed.substring(i, j).trim();
            if ("FT".equals(uom)) {
                elevation.setUom(UOM.FT);
            }

            // get the reference
            String reference = ed.substring(j).trim();
            if ("ALT".equals(reference)) {
                elevation.setReference(ElevationReference.MSL);
            } else if ("AGL".equals(reference)) {
                elevation.setReference(ElevationReference.SFC);
            }
        }

        return elevation;
    }

    /**
     * Process a distance description.
     *
     * @param distDesc a textual distance description
     * @return the distance described by the description
     */
    private Distance processDistance(String distDesc) {
        String dd = distDesc.trim();

        Distance distance = new Distance();

        if (dd.endsWith("KM")) {
            distance.setDistance(Double.parseDouble(
                    dd.substring(0, dd.length() - 2)) * 1000.0);
            distance.setUom(UOM.M);
        }

        return distance;
    }

    /**
     * Process a circle boundary description.
     *
     * @param circleDesc the textual circle description
     * @return the circle boundary described by the airspace
     * @throws ParseException on parsing errors
     */
    Circle processCircle(String circleDesc) throws ParseException {
        String cd = circleDesc.trim();

        Circle circle = new Circle();

        int i = cd.indexOf(CIRCLE_PREFIX);
        int j = cd.indexOf(CIRCLE_INFIX);
        if (i < 0) {
            throw new ParseException("Circle description missing prefix");
        }
        if (j < 0) {
            throw new ParseException("Circle description missing infix");
        }
        String radiusStr = cd.substring(i + CIRCLE_PREFIX.length(), j).trim();
        circle.setRadius(processDistance(radiusStr));

        String centerStr = cd.substring(j + CIRCLE_INFIX.length() + 1).trim();
        circle.setCenter(processPoint(centerStr));

        return circle;
    }

    /**
     * Return an airspace type from an airspace designation. This is done
     * by getting the substring after the country code in the designation
     * (e.g. "LH"), but before any airspace numeric id. For example, from
     * "LHTRA23A" this would return "TRA".
     *
     *  @param designator the airspace designator string
     *  @return the airspace type.
     */
    private String getAirspaceType(String designator) {
        int i = 2;

        for (; i < designator.length(); ++i) {
            if (Character.isDigit(Character.valueOf(designator.charAt(i)))) {
                break;
            }
        }

        return designator.substring(2, i);
    }

    /**
     *  Process an airspace definition from the aAIP.
     *
     *  @param airspaceNode the XML node that represents the airspace
     *         which is an &lt;x:tr&gt; node
     *  @return an airspace described by the node
     *  @throws ParseException on input parsing errors.
     */
    Airspace processAirspace(Node airspaceNode) throws ParseException {

        try {
            Airspace airspace = new Airspace();

            XPath xpath = XPathFactory.newInstance().newXPath();

            // get the name & designator
            String str = xpath.evaluate("td/strong", airspaceNode);
            int    i   = str.indexOf("/");
            if (i == -1) {
                throw new ParseException("no airspace designator");
            }
            String designator = str.substring(0, i).trim();
            String name       = str.substring(i + 1).trim();
            String type       = getAirspaceType(designator);

            airspace.setDesignator(designator);
            airspace.setName(name);
            airspace.setType(type);

            // get the boundary
            xpath.reset();
            str = xpath.evaluate("td/text()", airspaceNode);
            Boundary boundary = null;
            if (str.startsWith(CIRCLE_PREFIX)) {
                boundary = processCircle(str);
            } else {
                boundary = processPointList(designator, str);
            }

            airspace.setBoundary(boundary);

            // get the vertical limits
            xpath.reset();
            str = xpath.evaluate("td[position()=2]", airspaceNode);
            i   = str.indexOf("/");
            Elevation upperLimit = processElevation(str.substring(0, i).trim());
            Elevation lowerLimit = processElevation(
                                                str.substring(i + 1).trim());

            airspace.setUpperLimit(upperLimit);
            airspace.setLowerLimit(lowerLimit);

            // get the remarks
            xpath.reset();
            str = xpath.evaluate("td[position()=3]/text()[position()=2]",
                                 airspaceNode);
            airspace.setRemarks(str);

            return airspace;
        } catch (Exception e) {
            throw new ParseException(e);
        }
    }

    /**
     *  Process an eAIP file.
     *
     *  @param eAipNode the document node of an eAIP file
     *  @return a list of airspaces described by the document
     *  @throws ParseException on input parsing errors.
     */
    public List<Airspace> processEAIP(Node eAipNode) throws ParseException {

        List<Airspace> airspaces = new ArrayList<Airspace>();

        try {
            XPath          xpath     = XPathFactory.newInstance().newXPath();

            // get the name & designator
            NodeList nodes = (NodeList) xpath.evaluate(
              "//table/tbody/tr"
            + "[not(descendant::processing-instruction('Fm')"
                             + "[contains(., 'APSToBeDeleted')])]",
              eAipNode,
              XPathConstants.NODESET);

            for (int i = 0; i < nodes.getLength(); ++i) {
                try {
                    Airspace airspace = processAirspace(nodes.item(i));
                    airspaces.add(airspace);
                } catch (ParseException e) {
                    continue;
                }
            }
        } catch (Exception e) {
            throw new ParseException(e);
        }


        return airspaces;
    }
}