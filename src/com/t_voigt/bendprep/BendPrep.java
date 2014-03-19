package com.t_voigt.bendprep;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
//package bendprep;

import java.util.LinkedList;
import java.io.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerException;
import org.w3c.dom.*;
import java.awt.geom.Point2D;
//import java.awt.geom.Path2D;
import java.util.StringTokenizer;
import org.w3c.dom.svg.SVGPoint;
import java.net.URI;
import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.dom.svg.SVGOMPathElement;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.gvt.GraphicsNode;


/**
 *
 * @author Timo Voigt
 */
public class BendPrep {


    private static LinkedList<Point2D> hull;
    private static BendPrepJob job;

    
    public static void prep(String file, String configfile, ParameterDummy dummy) {
        System.out.println("Attention:If you used Adobe Illustrator to create your design, scale it up 125% to work in Visicut/Inkscape");
        //TODO: Auto scale for illustrator
        try {
            String filename = file.replace(".svg", "");
            
            URI fileURI = new File(filename + ".svg").toURI();

            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
            SVGDocument svgDoc = (SVGDocument) f.createDocument( fileURI.toString() );
            UserAgent      userAgent;
            DocumentLoader loader;
            BridgeContext  ctx;
            GVTBuilder     builder;
            GraphicsNode   rootGN;

            userAgent = new UserAgentAdapter();
            loader    = new DocumentLoader(userAgent);
            ctx       = new BridgeContext(userAgent, loader);
            ctx.setDynamicState(BridgeContext.DYNAMIC);
            builder   = new GVTBuilder();
            rootGN    = builder.build(ctx, svgDoc);
            
            hull = new LinkedList<>();
            job = new BendPrepJob(filename, configfile, svgDoc, dummy);

            String chosenBendId = job.getBendId();
            String chosenCutId = job.getCutId();
            createTolerance(chosenBendId, chosenCutId, svgDoc, filename);
            double focus = moveBendLine(svgDoc, chosenBendId);
            multiplyBendingLines(filename, svgDoc, chosenBendId);
            
            
            //TODO: write to plf file
            //write to file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(svgDoc);
            StreamResult result = new StreamResult(new File(filename + "_FocusHeight" + focus + ".svg").getAbsolutePath());
            transformer.transform(source, result);
            System.out.println("File written to:");
            System.out.println(filename + "_FocusHeight" + focus + ".svg");
            
            outputParameters();
            //createLSFile(job);
            System.gc();

        } catch (Exception e) {
            //TODO: Exception handling
            e.printStackTrace();
            //System.err.println(e.getMessage());
            //System.exit(1);
        }
    }
    
    static void outputParameters() {
        LinkedList<Point2D> bendline = job.getObj(job.getBendId()).getPoints();
        double length = ptTomm(distance(bendline.get(0), bendline.get(1)));
        int power = getPower(length);
        int speed = getSpeed(length);
        if(length > 120.0) {
            System.out.println("Attention! Line may be too long to be bent!");
            System.out.println("The following parameter may not work!");
        }
        System.out.println("Bend with following Parameters:");
        System.out.println(power + "% Power");
        System.out.println(speed + "% Speed");
        System.out.println("500 Frequency");
    }
    
    static int getPower(double length) {
        double c = 6.072005465;
        double b = -0.021584160;
        double a = 0.001057781;
        double pwr = a*length*length + b*length + c;
        System.out.println("length " + length);
        System.out.println("pwr " + pwr);
        return (int) Math.round(pwr);
    }
    
    static int getSpeed(double length) {
        
        return 80;
    }
    
    static void createLSFile(BendPrepJob job) throws IOException {
        //TODO: Laserscript sinnvoll?
        FileWriter fstream = new FileWriter(job.getJobname() + "_part1.ls");
        BufferedWriter out = new BufferedWriter(fstream);
        LinkedList<Point2D> pts = job.getObj("outer").getPoints();
        out.write("set(\"speed\",100);\n");
        out.write("set(\"power\",80);\n");
        createFilePart(out, pts);
        pts = job.getObj(job.getCutId()).getPoints();
        createFilePart(out,pts);
        
        out.close();
    }
    
    static void createFilePart(BufferedWriter out, LinkedList<Point2D> pts) throws IOException{
        Point2D next = pts.get(0);
        out.write("move(" + next.getX() + "," + next.getY() + ");\n");
        for(int i = 1; i < pts.size(); i++) {
            next = pts.get(i);
            out.write("line(" + next.getX() + "," + next.getY() + ");\n");
        }
    }


    static double moveBendLine(Document doc, String colorId) throws IOException {
        //angles for laser in x- and y-direction
        double alpha = -job.getXDiv();
        double beta = -job.getYDiv();
        double z = job.getFocusHeight();
        double dx = z * Math.tan(alpha * Math.PI / 180);
        double dy = z * Math.tan(beta * Math.PI / 180);
        dx = mmTopt(dx);
        dy = mmTopt(dy);
        Element child = doc.getElementById(colorId);
        double x1 = Double.parseDouble(child.getAttribute("x1"));
        x1 += dx;
        child.setAttribute("x1", Double.toString(x1));
        double x2 = Double.parseDouble(child.getAttribute("x2"));
        x2 += dx;
        child.setAttribute("x2", Double.toString(x2));
        double y1 = Double.parseDouble(child.getAttribute("y1"));
        y1 += dy;
        child.setAttribute("y1", Double.toString(y1));
        double y2 = Double.parseDouble(child.getAttribute("y2"));
        y2 += dy;
        child.setAttribute("y2", Double.toString(y2));
        
        return z;
    }

    static String getColorInkscape(Element child) {
        if(!(child.getNodeName().equals("polyline") || child.getNodeName().equals("path") || child.getNodeName().equals("line")))
            return "";
        String color;
        String style = child.getAttribute("style");
        if(style.isEmpty()) {
            System.err.println("File is invalid");
            //System.exit(1);
        }
        StringTokenizer st = new StringTokenizer(style, ";");
        while(st.hasMoreElements()) {
            StringTokenizer st2 = new StringTokenizer(st.nextToken(), ":");
            String type = st2.nextToken();
            if(type.equals("stroke")) {
                color = st2.nextToken();
                return color;
            }
        }
        return "";
    }

    static void createTolerance(String bendId, String cutId, Document doc, String filename)
        throws IOException, TransformerException {
        LinkedList<Point2D> outerPoints = createOuterPoints(job.getObj(cutId), job.getObj(bendId), false);
        // add node
        String pointStr = pointsToString(outerPoints);


        Element child = doc.getElementById(cutId);
        Element newNode =  doc.createElement("polyline");
        newNode.setAttribute("fill","none");
        newNode.setAttribute("stroke",job.getObj(cutId).getColor());
        newNode.setAttribute("points", pointStr);
        child.getParentNode().appendChild(newNode);
        job.addObj("outer", new BendObject("outer", job.getObj(cutId).getColor(), outerPoints));
        
        Element bend = doc.createElement("line");
        Element oldbend = doc.getElementById(bendId);
        bend.setAttribute("fill","none");
        bend.setAttribute("stroke", job.getObj(bendId).getColor());
        bend.setAttribute("x1", Double.toString(job.getObj(bendId).getPoints().get(0).getX()));
        bend.setAttribute("y1", Double.toString(job.getObj(bendId).getPoints().get(0).getY()));
        bend.setAttribute("x2", Double.toString(job.getObj(bendId).getPoints().get(1).getX()));
        bend.setAttribute("y2", Double.toString(job.getObj(bendId).getPoints().get(1).getY()));
        bend.setAttribute("id", bendId);
        bend.setIdAttribute("id", true);
        //child.getParentNode().appendChild(bend);
        //oldbend.getParentNode().removeChild(oldbend);
        oldbend.getParentNode().replaceChild(bend, oldbend);
        





    }

    static LinkedList<Point2D> parsePathData(String pathData, SVGDocument svgDoc, double epsilon, String id, String color) throws IOException {
        /*PathParser p = new PathParser();
        AWTPathProducer awt = new AWTPathProducer();
        p.setPathHandler(awt);
        Reader read = new StringReader(pathData);
        ExtendedGeneralPath path = new ExtendedGeneralPath(AWTPathProducer.createShape(read, 0));
        Rectangle2D bound = path.getBounds2D();

        double width = bound.getWidth();
        double height = bound.getHeight();
        String p1,p2,p3,p4;
        p1 = bound.getX() + "," + bound.getY();
        p2 = Double.toString(bound.getX() + width) + "," + bound.getY();
        p3 = Double.toString(bound.getX() + width) + "," + Double.toString(bound.getY() + height) ;
        p4 = bound.getX() + "," + Double.toString(bound.getY() + height);
        String ret = p1 + " " + p2 + " " + p3 + " " + p4;
        //System.out.println(ret);
        return ret;
         */
        
        
        
        SVGOMPathElement p = null;
        NodeList allNodes = svgDoc.getElementsByTagName("*");
        int length = allNodes.getLength();
        for(int i=0; i<length; i++){
            Node node = allNodes.item(i);
            if(node instanceof Element){
            //a child element to process
                Element child = (Element) node;
                String attrValue = child.getAttribute("stroke");
                if (attrValue.isEmpty()) {
                    attrValue = getColorInkscape(child);
                }
                //get cutting line
                if(attrValue.equalsIgnoreCase(color)) {
                    p = (SVGOMPathElement) child;
                    break;
                }
            }
        }
        
        String points = getPathPoints(p, epsilon);
        LinkedList<Point2D> points2 = getPoints(points);
        return points2;

    }

    static String getPathPoints(SVGOMPathElement p, double epsilon) {
        String ret = "";
        float segs = (float) epsilon * p.getTotalLength();
        for(float i = 0; i < p.getTotalLength(); i += segs) {
            SVGPoint next = p.getPointAtLength(i);
            ret += Double.toString(Math.round(next.getX()*100.)/100.0) + "," + Double.toString(Math.round(next.getY()*100.)/100.0) + " ";
        }
        SVGPoint next = p.getPointAtLength(p.getTotalLength());
        ret += Double.toString(Math.round(next.getX()*100.)/100.0) + "," + Double.toString(Math.round(next.getY()*100.)/100.0) + " ";
        return ret;
    }

    static String pointsToString(LinkedList<Point2D> pts) {
        String ret = "";
        for(int i = 0; i < pts.size(); i++) {
            String x = Double.toString(pts.get(i).getX());
            String y = Double.toString(pts.get(i).getY());
            ret += x + "," + y + " ";
        }
        return ret;
    }
    
    @Deprecated
    static LinkedList<Point2D> createOuterPoints2(BendObject cut, BendObject bend, boolean isPath) {
        
        //create convex hull
        createConvexHull(bend.getPoints());

        //printPoints(pts);
        double offset = job.getOffset();

        if(isPath) {
            //sort lines intersecting bendLine first
            //return hull;
        }
        //printPoints(hull);
        //System.out.println();
        MyLine2D bendLine = new MyLine2D(bend.getPoints().get(0).getX(),bend.getPoints().get(0).getY(),bend.getPoints().get(1).getX(),bend.getPoints().get(1).getY());
        sortHull(bendLine);
        //printPoints(hull);
        //TODO convert hullpoints to lines
        LinkedList<MyLine2D> shape = getShape(hull);
        //printLines(shape);
        //System.out.println();
        //delete line identical with bending line
        deleteBendLine(shape, bendLine);
        if(isPath) {
            //deleteOverhangLines(shape, bendLine);
        }
        printLines(shape);
        // get intersections of cutting lines and bending line
        LinkedList<Point2D> outerPoints = new LinkedList<>();
        boolean first = true;
        //printLines(shape);
        for(int i = 0; i < shape.size(); i++) {
            Point2D intersection = getIntersection(shape.get(i), bendLine);
            if (intersection != null && first){
                //first intersection with bending line == first line
                outerPoints.add(intersection);
                outerPoints.add(getPointAlongBendLine(bendLine, intersection, offset));
                Point2D corner = getCornerPoint(shape.get(i).getP1(), shape.get(i).getP2(), shape.get(i+1).getP2(), offset);
                if(!outerPoints.contains(corner) && !(Double.isNaN(corner.getX()) || Double.isNaN(corner.getY())))
                    outerPoints.add(corner);
                first = false;
            } else if (intersection != null && !first) {
                //second intersection with bending line == last line
                Point2D next = getCornerPoint(shape.get(i-1).getP1(), shape.get(i).getP1(), shape.get(i).getP2(), offset);
                if (!outerPoints.contains(next) && !(Double.isNaN(next.getX()) || Double.isNaN(next.getY())))
                    outerPoints.add(next);
                outerPoints.add(getPointAlongBendLine(bendLine, intersection, offset));
                outerPoints.add(intersection);
            } else {
                //any other line
                Point2D firstPoint = getCornerPoint(shape.get(i-1).getP1(), shape.get(i).getP1(), shape.get(i).getP2(), offset);
                Point2D secondPoint = getCornerPoint(shape.get(i).getP1(), shape.get(i).getP2(), shape.get(i+1).getP2(), offset);
                if (!outerPoints.contains(firstPoint) && !(Double.isNaN(firstPoint.getX()) || Double.isNaN(firstPoint.getY())))
                    outerPoints.add(firstPoint);
                if (!outerPoints.contains(secondPoint) && !(Double.isNaN(secondPoint.getX()) || Double.isNaN(secondPoint.getY())))
                    outerPoints.add(secondPoint);
            }
        }

        return outerPoints;

    }

    static LinkedList<Point2D> createOuterPoints(BendObject cut, BendObject bend, boolean isPath) {
        //create representation of existing Lines
        //create convex hull
        createConvexHull(cut.getPoints());

        //printPoints(pts);
        double offset = job.getOffset();
        MyLine2D bendLine = new MyLine2D(bend.getPoints().get(0).getX(),bend.getPoints().get(0).getY(),bend.getPoints().get(1).getX(),bend.getPoints().get(1).getY());
        sortHull(bendLine);
        LinkedList<MyLine2D> shape = getShape(hull);
        deleteBendLine(shape, bendLine);
        shapeToPoints(shape);
        LinkedList<Point2D> outerPoints = new LinkedList<>();
        Point2D mid = bendLine.getMidPoint();
        outerPoints.add(getIntersection(new MyLine2D(hull.get(0), hull.get(1)), bendLine));
        for(Point2D p : hull) {
            double x = p.getX() - mid.getX();
            double y = p.getY() - mid.getY();
            x = mid.getX() + x * offset;
            y = mid.getY() + y * offset;
            outerPoints.add(new Point2D.Double(x,y));
        }
        outerPoints.add(getIntersection(new MyLine2D(hull.get(hull.size() - 1), hull.get(hull.size() - 2)), bendLine));

        return outerPoints;

    }

    static void shapeToPoints(LinkedList<MyLine2D> shape) {
        LinkedList<Point2D> ret = new LinkedList<>();
        for(MyLine2D l: shape) {
            ret.add(l.getP1());
        }
        ret.add(shape.get(shape.size() - 1).getP2());
        hull = ret;
    }

    static void deleteOverhangLines(LinkedList<MyLine2D> lines, MyLine2D bendLine) {
        boolean first = false;
        for(int i = 0; i < lines.size(); i++) {
            Point2D intersec = getIntersection(lines.get(i), bendLine);
            if(intersec == null && !first){
                lines.remove(i);
            } else {
                first = true;
            }
        }

        first = false;
        for(int i = lines.size()-1; i >= 0; i--) {
            Point2D intersec = getIntersection(lines.get(i), bendLine);
            if(intersec == null && !first){
                lines.remove(i);
            } else {
                first = true;
            }
        }
    }

    static void deleteBendLine(LinkedList<MyLine2D> lines, MyLine2D bendLine) {
        double xMidBend = (bendLine.getP2().getX() + bendLine.getP1().getX()) / 2;
        double yMidBend = (bendLine.getP2().getY() + bendLine.getP1().getY()) / 2;
        Point2D midBend = new Point2D.Double(xMidBend, yMidBend);
        int index = 0;
        double minDist = Double.MAX_VALUE;
        for(int i = 0; i < lines.size(); i++) {
            double xMidLine = (lines.get(i).getP2().getX() + lines.get(i).getP1().getX()) / 2;
            double yMidLine = (lines.get(i).getP2().getY() + lines.get(i).getP1().getY()) / 2;
            Point2D midLine = new Point2D.Double(xMidLine, yMidLine);
            double dist = distance(midBend, midLine);
            if (dist < minDist) {
                minDist = dist;
                index = i;
            }
        }
        lines.remove(index);
    }

    static double deter(double x1, double y1, double x2, double y2, double x3, double y3){
        double a,b,c,d,e,f;
        a = x1 * y2;
        b = y1 * x3;
        c = x2 * y3;
        d = x3 * y2;
        e = y3 * x1;
        f = x2 * y1;
        return a + b + c - d - e - f;
    }

    static void sortHull(MyLine2D bendline) {
        LinkedList<Point2D> sorted = new LinkedList<>();
        roundHull();
        int startIndex = 0;
        double minDist = Double.MAX_VALUE;
        Point2D minDistPoint = null;
        //sortieren vorwaerts oder rueckwaerts?
        for(int i = 0; i < hull.size() - 1; i++) {
            Point2D intersec = getIntersection(new MyLine2D(hull.get(i), hull.get(i+1)), bendline);
            if (intersec != null)  {
                boolean before = areParallel(hull.get((((i-1)%hull.size())+hull.size())%hull.size()), hull.get(i), bendline.getP1(), bendline.getP2());
                boolean after = areParallel(hull.get((i+1)%hull.size()), hull.get((i+2)%hull.size()), bendline.getP1(), bendline.getP2());
                if(before && after) {
                    if(lineToPointDist(bendline.getP1(), bendline.getP2(), hull.get(i)) == 0)
                        startIndex = i;
                    else
                        startIndex = i+1;
                } else if(before) {
                    startIndex = i;
                } else if (after) {
                    startIndex = i+1;
                }
            }
        }
        for(int i = 0; i< hull.size(); i++) {
            sorted.add(hull.get((i + startIndex)%hull.size()));
        }
        hull = sorted;
    }

    static void roundHull() {
        LinkedList<Point2D> rounded = new LinkedList<>();
        for(int i = 0; i < hull.size(); i++) {
            double x = hull.get(i).getX();
            double y = hull.get(i).getY();
            x = Math.round(x * 100.) / 100.;
            y = Math.round(y * 100.) / 100.;
            Point2D r = new Point2D.Double(x,y);
            rounded. add(r);
        }
        hull = rounded;
    }

    static LinkedList<MyLine2D> getShape(LinkedList<Point2D> pts) {
        LinkedList<MyLine2D> lines = new LinkedList<>();
        for(int i = 0; i < pts.size() - 1; i++) {
            MyLine2D line = new MyLine2D(pts.get(i), pts.get(i+1));
            lines.add(line);
        }
        MyLine2D line = new MyLine2D(pts.get(pts.size()-1), pts.get(0));
        lines.add(line);
        return lines;
    }


    static Point2D getCornerPoint(Point2D p1, Point2D p2, Point2D p3, double offset) {
        double[] p2p1 = new double[2];
        double[] p2p3 = new double[2];
        double[] dir = new double[2];
        p2p1[0] = p1.getX() - p2.getX();
        p2p1[1] = p1.getY() - p2.getY();
        p2p3[0] = p3.getX() - p2.getX();
        p2p3[1] = p3.getY() - p2.getY();
        double magnp2p1 = magnitude(p2p1);
        double magnp2p3 = magnitude(p2p3);
        p2p1[0] = p2p1[0] / magnp2p1;
        p2p1[1] = p2p1[1] / magnp2p1;
        p2p3[0] = p2p3[0] / magnp2p3;
        p2p3[1] = p2p3[1] / magnp2p3;
        dir[0] = p2p1[0] + p2p3[0];
        dir[1] = p2p1[1] + p2p3[1];
        double magndir = magnitude(dir);
        dir[0] = dir[0]/magndir;
        dir[1] = dir[1]/magndir;
        double x = p2.getX() - mmTopt(offset) * dir[0];
        double y = p2.getY() - mmTopt(offset) * dir[1];
        return new Point2D.Double(x, y);
    }

    static Point2D getPointAlongBendLine(MyLine2D bendLine, Point2D intersec, double offset) {
        double d1 = distance(bendLine.getP1(), intersec);
        double d2 = distance(bendLine.getP2(), intersec);
        Point2D direction;
        if (d1 > d2) {
            direction = bendLine.getP2();
        } else {
            direction = bendLine.getP1();
        }
        //erzeuge punkt entlang gerade in offset mm
        return createPointWithOffset(intersec, direction, offset);
    }

    //TODO: an einfachem Vektorbeispiel testen!!!
    static Point2D createPointWithOffset(Point2D p1, Point2D p2, double offset) {
        double[] p1p2 = new double[2];
        p1p2[0] = p2.getX() - p1.getX();
        p1p2[1] = p2.getY() - p1.getY();
        double magnp1p2 = magnitude(p1p2);
        p1p2[0] = p1p2[0] / magnp1p2;
        p1p2[1] = p1p2[1] / magnp1p2;
        double x, y;
        x = p1.getX() + mmTopt(offset) * p1p2[0];
        y = p1.getY() + mmTopt(offset) * p1p2[1];
        return new Point2D.Double(x, y);
    }

    //TODO too many lines
    //computes convex hull with quickhull algorithm
    static void createConvexHull(LinkedList<Point2D> pts){
        LinkedList<Point2D> points = new LinkedList<>();
        //get extreme points in x direction, form middle line
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        Point2D minPoint = null;
        Point2D maxPoint = null;
        for (Point2D pt : pts) {
            points.add(pt);
            if (pt.getX() < min) {
                min = pt.getX();
                minPoint = pt;
            } else if (pt.getX() > max) {
                max = pt.getX();
                maxPoint = pt;
            }
        }
        points.remove(minPoint);
        points.remove(maxPoint);
        hull.add(minPoint);
        hull.add(maxPoint);
        //divide into points left and right from middle line
        LinkedList<Point2D> left = getPointsLeft(points, minPoint, maxPoint);
        LinkedList<Point2D> right = getPointsRight(points, minPoint, maxPoint);

        quickhull(left, minPoint, maxPoint);
        quickhull(right, minPoint, maxPoint);
        //convert points to lines

    }

    static void quickhull(LinkedList<Point2D> pts, Point2D p1, Point2D p2) {
        if (pts.isEmpty()) {
/*            MyLine2D line = new MyLine2D(p1,p2);
            if (!(hull.contains(line)))
                hull.add(line);*/
            return;
        }
        double maxDist = -Double.MAX_VALUE;
        Point2D maxPoint = null;
        LinkedList<Point2D> points = new LinkedList<>();
        for(int i = 0; i < pts.size(); i++) {
            points.add(pts.get(i));
            double dist = lineToPointDist(p1, p2, pts.get(i));
            if(dist > maxDist) {
                maxDist = dist;
                maxPoint = pts.get(i);
            }
        }
        points.remove(maxPoint);
        if(!hull.contains(maxPoint)) {
            if (hull.get(hull.indexOf(p1) + 1).equals(p2))
                hull.add(hull.indexOf(p1) + 1,maxPoint);
            else
                hull.add(hull.indexOf(p1),maxPoint);

        }
        LinkedList<Point2D> points2 = new LinkedList<>();
        //delete points inside triangle
        double area = 1/2*(-p1.getY()*p2.getX() + maxPoint.getY()*(-p1.getX() + p2.getX()) + maxPoint.getX()*(p1.getY() - p2.getY()) + p1.getX()*p2.getY());
        for(int i = 0; i < points.size(); i++) {
            double s = 1/(2*area)*(maxPoint.getY()*p2.getX() - maxPoint.getX()*p2.getY() + (p2.getY() - maxPoint.getY())*points.get(i).getX() + (maxPoint.getX() - p2.getX())*points.get(i).getY());
            double t = 1/(2*area)*(maxPoint.getX()*p1.getY() - maxPoint.getY()*p1.getX() + (maxPoint.getY() - p1.getY())*points.get(i).getX() + (p1.getX() - maxPoint.getX())*points.get(i).getY());
            if(!(s >= 0 && t >= 0 && (1-s-t)>=0))
                points2.add(points.get(i));
        }

        LinkedList<Point2D> left = getPointsRight(points2, p1, maxPoint, p2);
        LinkedList<Point2D> right = getPointsRight(points2, p2, maxPoint, p1);
        quickhull(left, p1, maxPoint);
        quickhull(right, maxPoint, p2);

    }

    static double lineToPointDist(Point2D p1, Point2D p2, Point2D a) {
        double dist  = distance(p1, p2);
        double cross = crossProduct(p1, p2, a);
        dist = Math.abs(cross / dist);
        return dist;
    }

    static double crossProduct(Point2D p1, Point2D p2, Point2D a) {
        double[] p1p2 = new double[2];
        double[] p1a = new double[2];
        p1p2[0] = p2.getX() - p1.getX();
        p1p2[1] = p2.getY() - p1.getY();
        p1a[0] = a.getX() - p1.getX();
        p1a[1] = a.getY() - p1.getY();
        double cross = p1p2[0] * p1a[1] - p1p2[1] * p1a[0];
        return cross;
    }

    static boolean areParallel(Point2D p1, Point2D p2, Point2D a1, Point2D a2) {
        double[] p1p2 = new double[2];
        double[] a1a2 = new double[2];
        p1p2[0] = p2.getX() - p1.getX();
        p1p2[1] = p2.getY() - p1.getY();
        a1a2[0] = a2.getX() - a1.getX();
        a1a2[1] = a2.getY() - a1.getY();
        double cross = p1p2[0] * a1a2[1] - p1p2[1] * a1a2[0];
        return cross == 0;
    }

    static double getAngle(Point2D p1, Point2D p2, Point2D p3) {
        double[] p1p2 = new double[2];
        double[] p1p3 = new double[2];
        p1p2[0] = p2.getX() - p1.getX();
        p1p2[1] = p2.getX() - p1.getY();
        p1p3[0] = p3.getX() - p1.getX();
        p1p3[1] = p3.getY() - p1.getY();
        double dot = p1p2[0] * p1p3[0] + p1p2[1] * p1p3[1];
        double abs1 = magnitude(p1p2);
        double abs2 = magnitude(p1p3);
        double angle = Math.acos(dot / (abs1 * abs2));
        return angle;
    }

    static double magnitude(double[] p){
        return Math.sqrt(p[0] * p[0] + p[1] * p[1]);
    }

    static double distance(Point2D p1, Point2D p2) {
        double d1 = p2.getX() - p1.getX();
        double d2 = p2.getY() - p1.getY();
        return Math.sqrt(d1 * d1 + d2 * d2);
    }

    static boolean isLeft(Point2D a, Point2D b, Point2D c){
     return ((b.getX() - a.getX())*(c.getY() - a.getY()) - (b.getY() - a.getY())*(c.getX() - a.getX())) > 0;
}

    static LinkedList<Point2D> getPointsLeft(LinkedList<Point2D> pts, Point2D p1, Point2D p2) {
        LinkedList<Point2D> left = new LinkedList<>();
        for(int i = 0; i < pts.size(); i++) {
            if(isLeft(p1,p2,pts.get(i)))
                left.add(pts.get(i));
        }
        return left;
    }

    static LinkedList<Point2D> getPointsRight(LinkedList<Point2D> pts, Point2D p1, Point2D p2) {
        LinkedList<Point2D> right = new LinkedList<>();
        for(int i = 0; i < pts.size(); i++) {
            if(!isLeft(p1,p2,pts.get(i)))
                right.add(pts.get(i));
        }
        return right;
    }

    static LinkedList<Point2D> getPointsRight(LinkedList<Point2D> pts, Point2D p1, Point2D p2, Point2D c) {
        LinkedList<Point2D> right = new LinkedList<>();
        LinkedList<Point2D> left = new LinkedList<>();
        pts.add(c);
        for(int i = 0; i < pts.size(); i++) {
            if(crossProduct(p1,p2,pts.get(i)) > 0)
                right.add(pts.get(i));
            else {
                left.add(pts.get(i));
            }
        }
        pts.remove(c);
        if(left.contains(c))
            return right;
        return left;
    }


    static Point2D getIntersection(MyLine2D line1, MyLine2D line2) throws RuntimeException {
        double m1, m2, t1, t2, x, y, dx1, dx2;
        boolean onLine1 = false;
        boolean onLine2 = false;
        dx1 = (line1.getX2() - line1.getX1());
        dx2 = (line2.getX2() - line2.getX1());
        Point2D sec;
        if(dx1 == 0 && dx2 != 0) {
            //line1 vertical
            x = line1.getX1();
            m2 = (line2.getY2() - line2.getY1()) / dx2;
            t2 = line2.getY1() - m2 * line2.getX1();
            y = m2 * x + t2;
            y = Math.round(y*1000.) / 1000.;
            onLine1 = (y <= line1.getY2() && y >= line1.getY1()) || (y >= line1.getY2() && y <= line1.getY1());
            if(PointIsOnLine(x, y, m2, t2)) {
                if ((y <= line2.getY2() && y >= line2.getY1()) || (y >= line2.getY2() && y <= line2.getY1())) {
                    onLine2 = (x <= line2.getX2() && x >= line2.getX1()) || (x >= line2.getX2() && x <= line2.getX1());
                } else {
                    onLine2 = false;
                }
            }
        } else if (dx1 != 0 && dx2 == 0) {
            //line2 vertical
            x = line2.getX1();
            m1 = (line1.getY2() - line1.getY1()) / dx1;
            t1 = line1.getY1() - m1 * line1.getX1();
            y = m1 * x + t1;
            y = Math.round(y*1000.) / 1000.;

            //check if intersection point is on the lines
            if(PointIsOnLine(x, y, m1, t1)) {
                if ((y <= line1.getY2() && y >= line1.getY1()) || (y >= line1.getY2() && y <= line1.getY1())) {
                    onLine1 = (x <= line1.getX2() && x >= line1.getX1()) || (x >= line1.getX2() && x <= line1.getX1());
                } else {
                    onLine1 = false;
                }
            }
            onLine2 = (y <= line2.getY2() && y >= line2.getY1()) || (y >= line2.getY2() && y <= line2.getY1());
        } else if (dx1 == 0 && dx2 == 0) {
            //both lines vertical
            //throw new RuntimeException("zero or unlimited intersections");
            return null;
        } else {
            m1 = (line1.getY2() - line1.getY1()) / dx1;
            m2 = (line2.getY2() - line2.getY1()) / dx2;
            t1 = line1.getY1() - m1 * line1.getX1();
            t2 = line2.getY1() - m2 * line2.getX1();
            if (m1 == m2) {
                //lines parallel or identical
                return null;
            }
            x = (t2 - t1) / (m1 - m2);
            y = m1 * x + t1;
            x = Math.round(x*1000.) / 1000.;
            y = Math.round(y*1000.) / 1000.;

            //check if intersection point is on the lines
            if(PointIsOnLine(x, y, m1, t1)) {
                if ((y <= line1.getY2() && y >= line1.getY1()) || (y >= line1.getY2() && y <= line1.getY1())) {
                    onLine1 = (x <= line1.getX2() && x >= line1.getX1()) || (x >= line1.getX2() && x <= line1.getX1());
                } else {
                    onLine1 = false;
                }
            }
            if(PointIsOnLine(x, y, m2, t2)) {
                if ((y <= line2.getY2() && y >= line2.getY1()) || (y >= line2.getY2() && y <= line2.getY1())) {
                    onLine2 = (x <= line2.getX2() && x >= line2.getX1()) || (x >= line2.getX2() && x <= line2.getX1());
                } else {
                    onLine2 = false;
                }
            }
        }
        if(!(onLine1 && onLine2))
            return null;
        sec = new Point2D.Double(x, y);
        return sec;
    }

    static boolean PointIsOnLine(double x, double y, double m, double t) {
        double a = Math.round(y * 1000.) / 1000.;
        double b = m*x + t;
        b = Math.round(b * 1000.) / 1000.;
        return a == b;
    }

    static LinkedList<Point2D> getBendingLine(Element node) {
        double x1,x2,y1,y2;
        x1 = Math.round(Double.parseDouble(node.getAttribute("x1"))*100.)/100.0;
        x2 = Math.round(Double.parseDouble(node.getAttribute("x2"))*100.)/100.0;
        y1 = Math.round(Double.parseDouble(node.getAttribute("y1"))*100.)/100.0;
        y2 = Math.round(Double.parseDouble(node.getAttribute("y2"))*100.)/100.0;
        Point2D p1 = new Point2D.Double(x1, y1);
        Point2D p2 = new Point2D.Double(x2, y2);
        LinkedList<Point2D> pts = new LinkedList<>();
        pts.add(p1);
        pts.add(p2);
        return pts;
    }


    // parses point string into Point2D[]
    static LinkedList<Point2D> getPoints(String points) {
        LinkedList<Point2D> pts = new LinkedList<>();
        StringTokenizer st = new StringTokenizer(points);
        while (st.hasMoreElements()) {
            StringTokenizer st2 = new StringTokenizer(st.nextToken(), ",");
            double[] pt = new double[2];
            int i = 0;
            while (st2.hasMoreElements()) {
                pt[i++] = Double.parseDouble(st2.nextToken());
            }
            Point2D p = new Point2D.Double(Math.round(pt[0]*1000.)/1000.0, Math.round(pt[1]*1000.)/1000.0);
            pts.add(p);
	}
        return pts;
    }

    static void multiplyBendingLines(String filename, Document doc, String colorId)
            throws IOException, TransformerException {
        Element child = doc.getElementById(colorId);
        for (int j = 0; j < 25; j++) {
            Element newNode = (Element) child.cloneNode(false);
            Element newNode2 = (Element) child.cloneNode(false);
            //spreadBendLine(newNode2);
            String a = newNode2.getAttribute("x1");
            String b = newNode2.getAttribute("y1");
            newNode2.setAttribute("x1", newNode2.getAttribute("x2"));
            newNode2.setAttribute("y1", newNode2.getAttribute("y2"));
            newNode2.setAttribute("x2", a);
            newNode2.setAttribute("y2", b);
            child.getParentNode().appendChild(newNode2);
            child.getParentNode().appendChild(newNode);
        } 
    }
    
    static void multiplyBendingLines2(String filename, Document doc, String colorId)
            throws IOException, TransformerException {
        Element child = doc.getElementById(colorId);
        //lines in opposing directions
        double ax1 = Double.parseDouble(child.getAttribute("x1"));
        double ay1 = Double.parseDouble(child.getAttribute("y1"));
        double ax2 = Double.parseDouble(child.getAttribute("x2"));
        double ay2 = Double.parseDouble(child.getAttribute("y2"));
        double bx1, bx2, by1, by2;
        bx1 = ax2;
        bx2 = ax1;
        by1 = ay2;
        by2 = ay1;
        
        //set start point 1/4 forward
        ax1 = ax1 + ((ax2 - ax1) * 1/4);
        ay1 = ay1 + ((ay2 - ay1) * 1/4);
        bx1 = bx1 + ((bx2 - bx1) * 1/4);
        by1 = by1 + ((by2 - by1) * 1/4);
        
        for (int j = 0; j < 25; j++) {
            Element newNode = (Element) child.cloneNode(false);
            Element newNode2 = (Element) child.cloneNode(false);
            //spreadBendLine(newNode2);
            newNode.setAttribute("x1", Double.toString(ax1));
            newNode.setAttribute("y1", Double.toString(ay1));
            newNode.setAttribute("x2", Double.toString(ax2));
            newNode.setAttribute("y2", Double.toString(ay2));
            
            newNode2.setAttribute("x1", Double.toString(bx1));
            newNode2.setAttribute("y1", Double.toString(by1));
            newNode2.setAttribute("x2", Double.toString(bx2));
            newNode2.setAttribute("y2", Double.toString(by2));
            child.getParentNode().appendChild(newNode2);
            child.getParentNode().appendChild(newNode);
        }
        child.getParentNode().removeChild(child);
    }

    static void spreadBendLine(Element node) {
        double x1 = Double.parseDouble(node.getAttribute("x1"));
        double x2 = Double.parseDouble(node.getAttribute("x2"));
        double y1 = Double.parseDouble(node.getAttribute("y1"));
        double y2 = Double.parseDouble(node.getAttribute("y2"));
        double vx = x2 - x1;
        double vy = y2 - y1;
        double px = 1.;
        double py = -vx / vy;
        double norm = Math.sqrt(px*px + py*py);
        px /= norm;
        py /= norm;
        x1 += 2 * px;
        x2 += 2 * px;
        y1 += 2 * py;
        y2 += 2 * py;
        node.setAttribute("x1", Double.toString(x1));
        node.setAttribute("x2", Double.toString(x2));
        node.setAttribute("y1", Double.toString(y1));
        node.setAttribute("y2", Double.toString(y2));

    }

    static void printPoints(Point2D[] pts) {
        for (Point2D pt : pts) {
            System.out.println("x: " + pt.getX() + " y: " + pt.getY());
        }
    }
    static void printPoints(LinkedList<Point2D> pts) {
        for(int i = 0; i < pts.size(); i++) {
            System.out.println("x: " + pts.get(i).getX() + " y: " + pts.get(i).getY());
        }
    }

    static void printLines(MyLine2D[] lines) {
        for (MyLine2D line : lines) {
            System.out.println("p1x: " + line.getX1() + " p1y: " + line.getY1() + "p2x: " + line.getX2() + " p2y: " + line.getY2());
        }
    }

    static void printLines(LinkedList<MyLine2D> lines) {
        for(int i = 0; i < lines.size(); i++) {
            System.out.format("%2f,%2f %2f,%2f\n",lines.get(i).getX1(), lines.get(i).getY1(), lines.get(i).getX2(), lines.get(i).getY2());
        }
    }

    static double mmTopt(double mm) {
        if(job.isIllustrator()) {
            return mm / 25.4 * 72;
        }
        return mm / 25.4 * 90;
    }

    static double ptTomm(double pt) {
        if(job.isIllustrator()) {
            return pt * 25.4 / 72;
        }
        return pt * 25.4 / 90;
    }

}
