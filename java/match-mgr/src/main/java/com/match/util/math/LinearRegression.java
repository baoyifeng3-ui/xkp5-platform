package com.match.util.math;

//测试类

import java.util.Scanner;

public class LinearRegression {
    private static final int MAX_POINTS = 10;//定义最大的训练集数据个数
    private double E;

    public static void main(String args[]) {   //测试主方法
        DataPoint[] data = new DataPoint[MAX_POINTS];  //创建数据集对象数组data[]
        //创建线性回归类对象line，并且初始化类
        RegressionLine line = new RegressionLine(constructDates(data));
        //调用printSums方法打印Sum变量
        printSums(line);
        //调用printLine方法并打印线性方程
        printLine(line);
    }

    //构建数据方法
    public static DataPoint[] constructDates(DataPoint date[]) {
        Scanner sc = new Scanner(System.in);
        double x, y;
        for (int i = 0; i < 3; i++) {
            System.out.println("请输入第" + (i + 1) + "个x的值：");
            x = sc.nextFloat();
            System.out.println("请输入第" + (i + 1) + "个y的值：");
            y = sc.nextFloat();
            date[i] = new DataPoint( x, y);
        }
        return date;
    }

    //打印Sum数据方法
    private static void printSums(RegressionLine line) {
        System.out.println("\n数据点个数 n = " +
                line.getDataPointCount());
        System.out.println("\nSumX = " + line.getSumX());
        System.out.println("SumY = " + line.getSumY());
        System.out.println("SumXX = " + line.getSumXX());
        System.out.println("SumXY = " + line.getSumXY());
        System.out.println("SumYY = " + line.getSumYY());
    }

    //打印回归方程方法
    private static void printLine(RegressionLine line) {
        System.out.println("\n回归线公式：y = " + line.getA1()
                + "x + " + line.getA0());
        //System.out.println("Hello World!");
        System.out.println("误差： R^2 = " + line.getR());
    }
}
