package com.match.util.math;

public class Point{
    double x;
    double y;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getDistance(Point p){
        double r = Math.sqrt(p.x-x)*(p.x-x)+(p.y-y)*(p.y-y);
        return r;
    }
}
