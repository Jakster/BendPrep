package com.t_voigt.bendprep;

import java.awt.Color;
import java.util.LinkedList;
import java.awt.geom.Point2D;



/**
 *
 * @author timo
 */
public class BendObject {
    private final String id;
    private final String color;
    private final Color rgbcolor;
    private final LinkedList<Point2D> pts;
    
    BendObject(String _id, String _color, LinkedList<Point2D> _pts) {
        this.id = _id;
        this.color = _color;
        this.rgbcolor = new Color(Integer.decode(_color));
        this.pts = _pts;
    }
    
    public String getId() {
        return id;
    }
    
    public String getColor() {
        return color;
    }
    
    public Color getRGBColor() {
        return rgbcolor;
    }
    
    public LinkedList<Point2D> getPoints() {
        return pts;
    }
    
    
    
}
