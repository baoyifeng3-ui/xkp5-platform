package com.match.util.math;

//RegressionLine类，用于处理一元线性回归问题

import java.math.BigDecimal;
import java.util.ArrayList;

public class RegressionLine {
    private Double sumX = 0d;//训练集x的和
    private Double sumY = 0d;//训练集y的和
    private Double sumXX = 0d;//x*x的和
    private Double sumYY = 0d;//y*y的和
    private Double sumXY = 0d;//x*y的和
    private Double sumDeltaY;//y与yi的差
    private Double sumDeltaY2; // sumDeltaY的平方和
    //误差
    private Double sse;//残差平方和
    private Double sst;//总平方和
    private Double E;
    private Double[] xy;
    private ArrayList<String> listX;//x的链表
    private ArrayList<String> listY;//y的链表
    private double XMin, XMax, YMin, YMax;
    private Double a0;//线性系数a0
    private Double a1;//线性系数a1
    private int pn;  //训练集数据个数
    private boolean coefsValid;

    //类RegressionLine的构造函数
    public RegressionLine() {
        XMax = 0;
        YMax = 0;
        pn = 0;
        xy = new Double[2];
        listX = new ArrayList<>();
        listY = new ArrayList<>();
    }

    //类RegressionLine的有参构造函数
    public RegressionLine(DataPoint data[]) {
        pn = 0;
        xy = new Double[2];
        listX = new ArrayList();
        listY = new ArrayList();
        for (int i = 0; i < data.length; ++i) {
            addDatapoint(data[i]);//添加数据集的方法addDatapoint
        }
    }

    public int getDataPointCount() {
        return pn;
    }

    public Double getA0() {
        validateCoefficients();
        return a0;
    }

    public Double getA1() {
        validateCoefficients();
        return a1;
    }

    public double getSumX() {
        return sumX;
    }

    public double getSumY() {
        return sumY;
    }

    public double getSumXX() {
        return sumXX;
    }

    public double getSumYY() {
        return sumYY;
    }

    public double getSumXY() {
        return sumXY;
    }

    public double getXMin() {
        return XMin;
    }

    public double getXMax() {
        return XMax;
    }

    public double getYMax() {
        return YMax;
    }

    public double getYMin() {
        return YMin;
    }

    //添加训练集数据的方法
    public void addDatapoint(DataPoint dataPoint) {
        sumX += dataPoint.x;
        sumY += dataPoint.y;
        sumXX += dataPoint.x * dataPoint.x;
        sumYY += dataPoint.y * dataPoint.y;
        sumXY += dataPoint.x * dataPoint.y;

        if (dataPoint.x > XMax) {
            XMax = dataPoint.x;
        }
        if (dataPoint.y > YMax) {
            YMax = dataPoint.y;
        }
        xy[0] = dataPoint.x;//?
        xy[1] = dataPoint.y;//?
        if (dataPoint.x != 0 && dataPoint.y != 0) {
            System.out.print("(" + xy[0] + ",");
            System.out.println(xy[1] + ")");
            try {
                listX.add(pn, String.valueOf(xy[0]));
                listY.add(pn, String.valueOf(xy[1]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ++pn;
        coefsValid = false;
    }

    //计算预测值y的方法
    public Double at(Double x) {
        if (pn < 2)
            return Double.NaN;
        validateCoefficients();
        return a0 + a1 * x;
    }

    //重置此类的方法
    public void reset() {
        pn = 0;
        sumX = sumY = sumXX = sumXY = 0d;
        coefsValid = false;
    }

    //计算系数a0，a1的方法
    private void validateCoefficients() {
        if (coefsValid)
            return;
        if (pn >= 2) {
            Double xBar = (Double) sumX / pn;
            Double yBar = (Double) sumY / pn;
            a1 = (Double) ((pn * sumXY - sumX * sumY) / (pn
                    * sumXX - sumX * sumX));
            a0 = (yBar - a1 * xBar);
        } else {
            a0 = a1 = Double.NaN;
        }
        coefsValid = true;
    }

    //计算判定系数R^2的方法
    public double getR() {
        for (int i = 0; i < pn; i++) {
            Double Yi = Double.parseDouble(listY.get(i).toString());
            Double Y = at(Double.parseDouble(
                    listX.get(i).toString()));
            Double deltaY = Yi - Y;
            Double deltaY2 = deltaY * deltaY;
            sumDeltaY2 += deltaY2;
            Double deltaY1 = (Yi - (Double) (sumY / pn)) * (Yi - (Double) (sumY / pn));
            sst += deltaY1;
        }
        //sst = sumYY - (sumY*sumY)/pn;
        E = 1 - sumDeltaY2 / sst;
        return round(E, 4);
    }

    //返回经处理过的判定系数的方法
    public double round(double v, int scale) {
        BigDecimal b = new BigDecimal(Double.toString(v));
        BigDecimal one = new BigDecimal("1");
        return b.divide(one, scale, BigDecimal.ROUND_HALF_UP).floatValue();
    }
}


