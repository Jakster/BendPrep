package com.t_voigt.bendprep;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 *
 * @author timo
 */
public class MyLine2D extends Line2D.Double {
    
    MyLine2D(double x1_, double y1_, double x2_, double y2_) {
        super(x1_, y1_, x2_, y2_);
    }
    MyLine2D(Point2D p1, Point2D p2) {
        super(p1, p2);
    }
    
    public Point2D getMidPoint() {
        double x = x2 + (x1 - x2) / 2;
        double y = y2 + (y1 - y2) / 2;
        return new Point2D.Double(x,y);
    }
    
    @Override
    public boolean equals(Object l){
        if (l == null)
            return false;
        if (l == this)
            return true;
        if (!(l instanceof Line2D))
            return false;
        Line2D l2 = (Line2D) l;
        if (!l2.getP1().equals(this.getP1()))
           return false;
        return l2.getP2().equals(this.getP2());
    }

    @Override
    public int hashCode() {
        int hash = 3;
        return hash;
    }
}
