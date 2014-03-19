package com.t_voigt.bendprep;

import static com.t_voigt.bendprep.BendPrep.getColorInkscape;
import java.util.HashMap;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.io.*;
import org.w3c.dom.*;
import java.util.StringTokenizer;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGRect;
/**
 *
 * @author Timo Voigt
 */
public class BendPrepJob {
    
    private HashMap<String,BendObject> objs = new HashMap<>();
    private String jobname;
    private String bendId;
    private String cutId;
    private double offset;
    private double focusHeight;
    private double accuracy;
    private double xdiv;
    private double ydiv;
    private boolean illustratorFile;
    
    
    
    BendPrepJob(String jobname_, String configfile, SVGDocument doc, ParameterDummy dummy) throws FileNotFoundException, IOException {
        this.jobname = jobname_;
        String bendColor = null;
        String cutColor = null;
        if (dummy == null) {
            FileReader fr = new FileReader(configfile);
            BufferedReader br = new BufferedReader(fr);
            String line;
            while ((line = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);
                String type = (String) st.nextElement();
                String value = (String) st.nextElement();
                //System.out.println(type + " " + value);
                switch (type) {
                    case "bendColor":   bendColor = value;
                                        break;
                    case "cutColor":    cutColor = value;
                                        break;
                    case "offset":      offset = Double.parseDouble(value);
                                        break;
                    case "focusHeight": focusHeight = Double.parseDouble(value);
                                        break;
                    case "accuracy":    accuracy = Double.parseDouble(value);
                                        break;
                    case "xdiv":        xdiv = Double.parseDouble(value);
                                        break;
                    case "ydiv":        ydiv = Double.parseDouble(value);
                                        break;
                }
            }
            if(bendColor == null || cutColor == null) {
                System.err.println("Error reading configfile");
                //System.exit(1);
            }
        } else if (configfile == null) {
            bendColor = dummy.bc;
            cutColor = dummy.cc;
            offset = dummy.os;
            focusHeight = dummy.fh;
            accuracy = dummy.ac;
            xdiv = dummy.xd;
            ydiv = dummy.yd;
        }
        
        //get Elements from SVG
        String polyLinePoints;
        String pathData;
        LinkedList<Point2D> bendLine;
        LinkedList<Point2D> points;
        int count = (int) (Math.random()*1000);
        
        Node root = doc.getDocumentElement();
        
        
        SVGRect bbox = doc.getRootElement().getBBox();
        float xmid = bbox.getX() + bbox.getWidth()/2;
        float ymid = bbox.getY() + bbox.getHeight()/2;
        
        NodeList allNodes = doc.getChildNodes();
        int length = allNodes.getLength();
        for(int i=0; i<length; i++){
            Node node = allNodes.item(i);
            if(node.getNodeType() == Node.COMMENT_NODE) {
                String text = node.getTextContent();
                if(text.toLowerCase().contains("illustrator"))
                    illustratorFile = true;
            }
        }
        
        //get all path Elements
        allNodes = doc.getElementsByTagName("path");
        length = allNodes.getLength();
        for(int i=0; i<length; i++){
            Node node = allNodes.item(i);
            if(node instanceof Element){
            //a child element to process
                Element child = (Element) node;
                String attrValue = child.getAttribute("stroke");
                if (attrValue.isEmpty()) {
                    attrValue = getColorInkscape(child);
                }
                //get cutting path
                if(attrValue.equalsIgnoreCase(cutColor)) {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "cut" + count++;
                    }
                    cutId = id;
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    pathData = child.getAttribute("d");
                    points = BendPrep.parsePathData(pathData, doc, accuracy, id, cutColor);
                    
                    //get transform if there is any
                    String trans = child.getAttribute("transform");
                    if(!trans.isEmpty()) {
                        points = transformMatrix(points, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        points = transformMatrix(points, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        points = transformMatrix(points, "matrix=(1.25,0,0,1.25,0,0)");
                        points = transformMatrix(points, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }*/
                    BendObject cut = new BendObject(id, cutColor, points);
                    objs.put(id, cut);
                }
                //get bending path
                else if (attrValue.equalsIgnoreCase(bendColor)) {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "bend" + count++;
                    }
                    bendId = id;
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    pathData = child.getAttribute("d");
                    bendLine = BendPrep.parsePathData(pathData, doc, 1., id, bendColor);
                    
                    //get transform if there is any
                    String trans = child.getAttribute("transform");
                    if(!trans.isEmpty()) {
                        bendLine = transformMatrix(bendLine, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        bendLine = transformMatrix(bendLine, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        bendLine = transformMatrix(bendLine, "matrix=(1.25,0,0,1.25,0,0)");
                        bendLine = transformMatrix(bendLine, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }*/
                    BendObject bend = new BendObject(id, bendColor, bendLine);
                    objs.put(id, bend);
                }
                
                //get other paths
                else  {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "path" + count++;
                    }
                    
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    pathData = child.getAttribute("d");
                    LinkedList<Point2D> path;
                    path = BendPrep.parsePathData(pathData, doc, accuracy, id, attrValue);
                    
                    //get transform if there is any
                    String trans = child.getAttribute("transform");
                    if(!trans.isEmpty()) {
                        path = transformMatrix(path, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        path = transformMatrix(path, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        path = transformMatrix(path, "matrix=(1.25,0,0,1.25,0,0)");
                        path = transformMatrix(path, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }
                    */
                    BendObject otherPath = new BendObject(id, attrValue, path);
                    objs.put(id, otherPath);
                }
            }
        }
        
        //get all polyline Elements
        allNodes = doc.getElementsByTagName("polyline");
        length = allNodes.getLength();
        for(int i=0; i<length; i++) {
            Node node = allNodes.item(i);
            if(node instanceof Element){
            //a child element to process
                Element child = (Element) node;
                String attrValue = child.getAttribute("stroke");
                if (attrValue.isEmpty()) {
                    attrValue = getColorInkscape(child);
                }
                //get cutting polyline
                if(attrValue.equalsIgnoreCase(cutColor)) {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "cut" + count++;
                    }
                    cutId = id;
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    polyLinePoints = child.getAttribute("points");
                    points = BendPrep.getPoints(polyLinePoints);
                    String trans = child.getAttribute("transform");
                    
                    if(!trans.isEmpty()) {
                        points = transformMatrix(points, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        points = transformMatrix(points, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        points = transformMatrix(points, "matrix=(1.25,0,0,1.25,0,0)");
                        points = transformMatrix(points, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }*/
                    BendObject cut = new BendObject(id, cutColor, points);
                    objs.put(id, cut);
                }
                
                //get other polylines
                else  {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "polyline" + count++;
                    }
                    
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    pathData = child.getAttribute("points");
                    LinkedList<Point2D> polyline;
                    polyline = BendPrep.getPoints(pathData);
                    
                    //get transform if there is any
                    String trans = child.getAttribute("transform");
                    if(!trans.isEmpty()) {
                        polyline = transformMatrix(polyline, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        polyline = transformMatrix(polyline, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        polyline = transformMatrix(polyline, "matrix=(1.25,0,0,1.25,0,0)");
                        polyline = transformMatrix(polyline, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }*/
                    BendObject otherPath = new BendObject(id, attrValue, polyline);
                    objs.put(id, otherPath);
                }
            }
        }
        
        //get all line elements
        allNodes = doc.getElementsByTagName("line");
        length = allNodes.getLength();
        for(int i=0; i<length; i++){
            Node node = allNodes.item(i);
            if(node instanceof Element){
            //a child element to process
                Element child = (Element) node;
                String attrValue = child.getAttribute("stroke");
                if (attrValue.isEmpty()) {
                    attrValue = getColorInkscape(child);
                }
                //get bending Line
                if (attrValue.equalsIgnoreCase(bendColor)) {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "bend" + count++;
                    }
                    bendId = id;
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    bendLine = BendPrep.getBendingLine(child);
                    
                    //get transform if there is any
                    String trans = child.getAttribute("transform");
                    if(!trans.isEmpty()) {
                        bendLine = transformMatrix(bendLine, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        bendLine = transformMatrix(bendLine, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        bendLine = transformMatrix(bendLine, "matrix=(1.25,0,0,1.25,0,0)");
                        bendLine = transformMatrix(bendLine, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }*/
                    BendObject bend = new BendObject(id, bendColor, bendLine);
                    objs.put(id, bend);
                }
                //other lines
                else  {
                    String id = child.getAttribute("id");
                    if(id.isEmpty()) {
                        id = "line" + count++;
                    }
                    
                    child.setAttribute("id", id);
                    child.setIdAttribute("id", true);
                    LinkedList<Point2D> polyline;
                    polyline = BendPrep.getBendingLine(child);
                    
                    //get transform if there is any
                    String trans = child.getAttribute("transform");
                    if(!trans.isEmpty()) {
                        polyline = transformMatrix(polyline, trans);
                    }
                    /*
                    //if Illustrator file, scale up 25% because of different dpi
                    if(illustratorFile) {
                        polyline = transformMatrix(polyline, "matrix=(0,0,0,0," + (-xmid) + "," + (-ymid) + ")");
                        polyline = transformMatrix(polyline, "matrix=(1.25,0,0,1.25,0,0)");
                        polyline = transformMatrix(polyline, "matrix=(0,0,0,0," + (xmid) + "," + (ymid) + ")");
                    }*/
                    BendObject otherPath = new BendObject(id, attrValue, polyline);
                    objs.put(id, otherPath);
                }
            }
        }
        
        if (cutId == null || bendId == null) {
            System.err.println("No suitable data found. Check your config-file!");
            //System.exit(1);
        }
    }
    
    private static LinkedList<Point2D> transformMatrix(LinkedList<Point2D> pts, String trans) {
        double [][] mat = parseTransformation(trans);
        LinkedList<Point2D> ret = new LinkedList<>();
        for( int i = 0; i < pts.size(); i++) {
            ret.add(matVecMult(mat, toArr(pts.get(i))));
        }
        return ret;
    }
    
    private static double[] toArr(Point2D p) {
        double [] ret = new double[3];
        ret[0] = p.getX();
        ret[1] = p.getY();
        ret[2] = 1.0;
        return ret;
    }
    
    private static Point2D matVecMult(double[][] mat, double[] vec) {
        double[] ret = new double[3];
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                ret[i] += mat[j][i] * vec[j];
            }
        }
        Point2D ret2 = new Point2D.Double(ret[0], ret[1]);
        return ret2;
    }
    
    private static double[][] parseTransformation(String trans) {
        StringTokenizer st = new StringTokenizer(trans, "(");
        double [][] mat = new double[3][3];
        mat[0][2] = 0.0;
        mat[1][2] = 0.0;
        mat[2][2] = 1.0;
        while(st.hasMoreElements()) {
            st.nextToken();
            StringTokenizer st2 = new StringTokenizer(st.nextToken(), ")");
            String numbers = st2.nextToken();
            StringTokenizer st3 = new StringTokenizer(numbers, ",");
            for(int i = 0; i < 3; i++) {
                for(int j = 0; j < 2; j++) {
                    String test = st3.nextToken();
                    mat[i][j] = Double.parseDouble(test);
                }
            }
        }
        
        return mat;
    }
    
    public BendObject getObj(String id) {
        return objs.get(id);
    }
    
    public void addObj(String id, BendObject b) {
        objs.put(id, b);
    }
    
    public String getJobname() {
        return jobname;
    }
    
    public String getBendId() {
        return bendId;
    }
    
    public String getCutId() {
        return cutId;
    }
    
    public double getOffset() {
        return offset;
    }
    
    public double getFocusHeight() {
        return focusHeight;
    }

    
    public double getAccuray() {
        return accuracy;
    }
    
    public double getXDiv() {
        return xdiv;
    }
    
    public double getYDiv() {
        return ydiv;
    }
    
    public boolean isIllustrator() {
        return illustratorFile;
    }
    
    private void setFocusHeight() throws IOException {
        System.out.print("Please type in focus height in mm: ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        focusHeight = Double.parseDouble(br.readLine());
        
    }
    
    private void setOffset() throws IOException {
        System.out.print("Please type in offset factor: ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        offset = Double.parseDouble(br.readLine());
        
        
    }
    
    private void setAccuracy() throws IOException {
        System.out.print("Please type in accuracy: ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        accuracy = Double.parseDouble(br.readLine());
        if (accuracy <= 0) {
            System.err.println("invalid epsilon, try again!");
            setAccuracy();
        }
        
    }
}
