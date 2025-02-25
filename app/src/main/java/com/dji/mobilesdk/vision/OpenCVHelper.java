package com.dji.mobilesdk.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Picture;
import android.util.Log;

import com.amap.api.maps.model.Poi;

import dji.common.flightcontroller.virtualstick.FlightControlData;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import static org.opencv.core.Core.BORDER_DEFAULT;
import static org.opencv.core.Core.bitwise_not;
import static org.opencv.core.Core.eigen;
import static org.opencv.core.Core.extractChannel;
import static org.opencv.core.Core.reduce;

import java.util.Arrays;

public class OpenCVHelper {
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private Context context;
    private MatOfPoint3f objPoints;
    private Mat intrinsic;
    private MatOfDouble distortion;
    private Mat logoImg;
    private MatOfPoint3f objectPoints;
    private int id_to_visit;
    private boolean has_taken_off;
    private Scalar last_direction;
    private int f1_count;
    private int f2_count;

    public OpenCVHelper(Context context) {
        this.context = context;
        this.id_to_visit = 1;
        this.has_taken_off = false;
        this.last_direction = new Scalar(0,0);
        this.f1_count = 0;
        this.f2_count = 0;
    }

    public Mat defaultImageProcessing(Mat input) {
        Imgproc.putText(input, "Default", new Point(150, 40), 1, 4, new Scalar(255, 255, 255), 2, 8, false);
        return input;
    }

    public Mat convertToGray(Mat input) {
        Mat output = new Mat();
        Imgproc.cvtColor(input, output, Imgproc.COLOR_RGBA2GRAY);
        return output;
    }

    public Mat detectEdgesUsingCanny(Mat input) {
        Mat output = new Mat();
        Imgproc.Canny(input, output, 80, 100);
        return output;
    }

    public Mat detectEdgesUsingLaplacian(Mat input) {
        Mat grayImg = new Mat();
        Mat intermediateMat = new Mat();
        Mat output = new Mat();
        Imgproc.cvtColor(input, grayImg, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(grayImg, grayImg, new Size(3, 3), 0, 0);
        Imgproc.Laplacian(grayImg, intermediateMat, CvType.CV_8U, 3, 1, 0, BORDER_DEFAULT);
        Core.convertScaleAbs(intermediateMat, intermediateMat, 10, 0);
        Imgproc.cvtColor(intermediateMat, output, Imgproc.COLOR_GRAY2RGBA, 4);
        grayImg.release();
        intermediateMat.release();
        return output;
    }

    public Mat blurImage(Mat input) {
        Mat grayMat = new Mat();
        Mat output = new Mat();
        Imgproc.cvtColor(input, grayMat, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(grayMat, output, new Size(35, 35), 0, 0);
        grayMat.release();
        return output;
    }

    public Mat detectFaces(Mat input, CascadeClassifier faceDetector) {
        Mat grayImgMat = new Mat();
        MatOfRect faces = new MatOfRect();
        Mat output;
        Imgproc.cvtColor(input, grayImgMat, Imgproc.COLOR_RGBA2GRAY);
        if (faceDetector != null) {
            faceDetector.detectMultiScale(grayImgMat, faces, 1.1, 2, 2, new Size(60, 60), new Size());
        }
        output = input;
        Rect[] facesArray = faces.toArray();
        for (Rect rect : facesArray) {
            Imgproc.rectangle(output, rect.tl(), rect.br(), FACE_RECT_COLOR, 3);
        }
        return output;
    }

    public Mat detectArucoTags(Mat input, Dictionary dictionary, DroneHelper droneHelper) {

        Mat grayImgMat = new Mat();
        Mat intermediateImgMat = new Mat();
        Mat output = new Mat();
        Imgproc.cvtColor(input, grayImgMat, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.cvtColor(input, intermediateImgMat, Imgproc.COLOR_RGBA2RGB);
        Mat ids = new Mat();
        List<Mat> corners = new ArrayList<>();
        Aruco.detectMarkers(grayImgMat, dictionary, corners, ids);

        if (ids.depth() > 0) {
            Aruco.drawDetectedMarkers(intermediateImgMat, corners, ids, new Scalar(255, 0, 255));
        }
        moveOnArucoDetected(ids, corners, droneHelper, intermediateImgMat.width(), intermediateImgMat.height());


        Imgproc.cvtColor(intermediateImgMat, output, Imgproc.COLOR_RGB2BGR);
        Imgproc.cvtColor(output, output, Imgproc.COLOR_BGR2RGBA, 4);
        grayImgMat.release();
        intermediateImgMat.release();
        return output;
    }

    private void moveOnArucoDetected(Mat ids,
                                     List<Mat> corners,
                                     DroneHelper droneHelper,
                                     int imageWidth,
                                     int imageHeight) {
        Log.d(" id ", id_to_visit+"");
        //TODO
        // Implement your logic to decide where to move the drone
        // Below snippet is an example of how you can calculate the center of the marker

        float yaw = 0.4f;

        if (id_to_visit == 18) {
            droneHelper.land();
            return;
        }

        Scalar markerCenter;

        if (ids.depth() == 0){
            droneHelper.moveVxVyYawrateHeight(0f, 0f, yaw, 3.3f);
            return;
        }

        int index = -1;
        for (int i = 0; i < ids.depth(); i++) {
            if (ids.get(i, 0) != null) {
                //System.out.println("ids " + ids.size() + "element size" + ids.get(i,0).length + "element "+ids.get(i,0)[0]);
                if (ids.get(i, 0)[0] == id_to_visit)
                    index = i;
            }
        }
        if (index == -1) {
            //Log.d("couldn't find id ", +id_to_visit + " image vector ");
            Scalar motionVector = convertImageVectorToMotionVector(last_direction);
            droneHelper.moveVxVyYawrateHeight((float) motionVector.val[0], (float) motionVector.val[1], 0, 3.3f);
            return;
        } else {
            markerCenter = Core.mean(corners.get(index));
        }
        // Codes commented below show how to drive the drone to move to the direction
        // such that desired tag is in the center of image frame

        // Calculate the image vector relative to the center of the image
        Scalar imageVector = new Scalar(markerCenter.val[0] - imageWidth / 2f, markerCenter.val[1] - imageHeight / 2f);

        // if distance less than threshold then ++
        // @todo tune distance
        double distance = Math.sqrt((imageVector.val[1] * imageVector.val[1]) + (imageVector.val[0] * imageVector.val[0]));
        if (distance < 160) {
            id_to_visit++;
            index = -1;
            for (int i = 0; i < ids.depth(); i++) {
                if (ids.get(i, 0) != null) {
                    if (ids.get(i, 0)[0] == id_to_visit)
                        index = i;
                }
            }
            if (index == -1) {
                Scalar motionVector = convertImageVectorToMotionVector(last_direction);
                droneHelper.moveVxVyYawrateHeight((float) motionVector.val[0], (float) motionVector.val[1], 0, 3.3f);
                return;
            } else {
                markerCenter = Core.mean(corners.get(index));
                imageVector = new Scalar(markerCenter.val[0] - imageWidth / 2f, markerCenter.val[1] - imageHeight / 2f);
                last_direction = imageVector;
            }
        }

        // Convert vector from image coordinate to drone navigation coordinate
        Scalar motionVector = convertImageVectorToMotionVector(imageVector);

        // If there's no tag detected, no motion required
//        if (ids.size().empty()) {
//            Scalar motionVector = convertImageVectorToMotionVector(last_direction);
//        }
        // depending on the mode you choose at the beginning of this function

        if ((imageVector.val[0] * imageVector.val[0] + imageVector.val[1] * imageVector.val[1]) < 900) {
            droneHelper.moveVxVyYawrateHeight((float) motionVector.val[0], (float) motionVector.val[1], 0, 3.3f);
        } else {
            droneHelper.moveVxVyYawrateHeight((float) motionVector.val[0], (float) motionVector.val[1], 0, 3.3f);
        }

        // Sample functions to help you control the drone such as takeoff and land

    }

    public void doDroneMoveUsingImage(Mat input, DroneHelper droneHelper) {
        /*
         * Remember this function is called every time
         * a frame is available. So don't do long loop here.
         */
        Imgproc.cvtColor(input, input, Imgproc.COLOR_BGR2YUV);
        extractChannel(input, input, 0);
        FlightControlData controlData =
                new FlightControlData(0.1f, 0.0f, 0.0f, 0.0f); //pitch, roll, yaw, verticalThrottle
        droneHelper.sendMovementCommand(controlData);
    }

    public Mat doAROnImage(Mat input, Dictionary dictionary, DroneHelper droneHelper) {
        //startDoAR(droneHelper);
        //TODO:
        // Since this is the bonus part, only high-level instructions will be provided
        // One way you can do this is to:
        // 1. Identify the Aruco tags with corner pixel location
        //    Hint:Aruco.detectMarkers(...)
        // 2. For each corner in 3D space, define their 3D locations
        //    The 3D locations you defined here will determine the origin of your coordinate frame
        // 3. Given the 3D locations you defined, their 2D pixel location in the image, and camera parameters
        //    You can calculate the 6 DOF of the camera relative to the tag coordinate frame
        //    Hint: Calib3d.solvePnP(...)
        // 4. To put artificial object in the image, you need to create 3D points first and project them into 2D image
        //    With the projected image points, you can draw lines or polygon
        //    Hint: Calib3d.projectPoints(...);
        // 5. To put dji image on a certain location,
        //    you need find the homography between the projected 4 corners and the 4 corners of the logo image
        //    Hint: Calib3d.findHomography(...);
        // 6. Once the homography is found, warp the image with perspective
        //    Hint: Imgproc.warpPerspective(...);
        // 7. Now you have the warped logo image in the right location, just overlay them on top of the camera image
        Mat output = new Mat();
        Mat grayMat = convertToGray(input);
        startDoAR(droneHelper);
        List<Mat> corners = new ArrayList<>();
        Aruco.detectMarkers(grayMat, dictionary, corners, output);
        if (corners.size() > 0) {
            Mat m = corners.get(corners.size() - 1);
            Point[] parr = new Point[4];
            for (int i = 0; i < m.cols(); i++) {
                parr[i] = new Point(m.get(0, i)[0], m.get(0, i)[1]);
            }
            MatOfPoint2f c = new MatOfPoint2f(parr[0], parr[1], parr[2], parr[3]);
            MatOfPoint2f rvec = new MatOfPoint2f();
            MatOfPoint2f tvec = new MatOfPoint2f();
            Calib3d.solvePnP(objPoints, c, intrinsic,
                    distortion, rvec, tvec);
            MatOfPoint2f imagePoints = new MatOfPoint2f();
            Calib3d.projectPoints(objectPoints, rvec, tvec, intrinsic, distortion, imagePoints);
            MatOfPoint2f square = new MatOfPoint2f(new Point(0, 0), new Point(1800, 0), new Point(1800, 1800), new Point(0, 1800));
            /*System.out.print("imagePoints: ");
            System.out.println(imagePoints);
            System.out.print("square: ");
            System.out.println(square);
           */
            Mat homo = Calib3d.findHomography(square, imagePoints);
            Mat logoWarped = new Mat();
            Imgproc.warpPerspective(logoImg, logoWarped, homo, input.size());
            Imgproc.cvtColor(logoWarped, grayMat, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(grayMat, grayMat, 0, 255, Imgproc.THRESH_BINARY);
            Mat grayInv = new Mat();
            Mat src1Final = new Mat();
            Mat src2Final = new Mat();
            bitwise_not(grayMat, grayInv);
            input.copyTo(src1Final, grayInv);
            logoWarped.copyTo(src2Final, grayMat);
/*            input.copyTo(src1Final);
            logoWarped.copyTo(src2Final);
            System.out.print("input: ");System.out.println(input.size());
            System.out.print("logoWarped: ");System.out.println(logoWarped.size());
            System.out.print("grayInv: ");System.out.println(grayInv.size());
            System.out.print("src1Final: ");System.out.println(src1Final.size());
            System.out.print("src2Final: ");System.out.println(src2Final.size());
            */
            Core.add(src1Final, src2Final, output);
        }

        if (output.empty()) {
            output = grayMat;
        }

        return output;
    }


    public void startDetectAruco(DroneHelper droneHelper) {
        // Virtual stick mode is a control interface
        // allows user to programmatically control the drone's movement
        droneHelper.enterVirtualStickMode();

        // This will change the behavior in the z-axis of the drone
        // If you call change set vertical mode to absolute height
        // Use moveVxVyYawrateHeight(...)
        // Otherwise use moveVxVyYawrateVz(...)
        droneHelper.setVerticalModeToAbsoluteHeight();
//        droneHelper.setVerticalModeToVelocity();

        // Move the camera to look down so you can see the tags if needed
        droneHelper.setGimbalPitchDegree(-75.0f);
    }

    public void startDroneMove(DroneHelper droneHelper) {
        droneHelper.enterVirtualStickMode();
        droneHelper.setVerticalModeToVelocity();
    }

    public void startDoAR(DroneHelper droneHelper) {
        droneHelper.enterVirtualStickMode();
        droneHelper.setVerticalModeToAbsoluteHeight();
        Bitmap bMap = BitmapFactory.decodeResource(context.getResources(), R.drawable.dji_logo);
        logoImg = new Mat();
        Utils.bitmapToMat(bMap, logoImg);

        //Camera calibration code
        intrinsic = new Mat(3, 3, CvType.CV_32F);
        intrinsic.put(0,
                0,
                1.2702029303551683e+03,
                0.,
                7.0369652952332717e+02,
                0.,
                1.2682183239938338e+03,
                3.1342369745005681e+02,
                0.,
                0.,
                1.);

        distortion = new MatOfDouble(3.2177759275048554e-02,
                1.1688831035623757e+00,
                -1.6742357543049650e-02,
                1.4173384809091350e-02,
                -6.1914718831876847e+00);

        // Please measure the marker size in Meter and enter it here
        double markerSizeMeters = 0.13;
        double halfMarkerSize = markerSizeMeters * 0.5;

        // Self-defined tag location in 3D, this is used in step 2 in doAR
        objPoints = new MatOfPoint3f();
        List<Point3> point3List = new ArrayList<>();
        point3List.add(new Point3(-halfMarkerSize, -halfMarkerSize, 0));
        point3List.add(new Point3(-halfMarkerSize, halfMarkerSize, 0));
        point3List.add(new Point3(halfMarkerSize, halfMarkerSize, 0));
        point3List.add(new Point3(halfMarkerSize, -halfMarkerSize, 0));
        objPoints.fromList(point3List);

        // AR object points in 3D, this is used in step 4 in doAR
        objectPoints = new MatOfPoint3f();

        List<Point3> point3DList = new ArrayList<>();
//        point3DList.add(new Point3(-halfMarkerSize, -halfMarkerSize, 0));
//        point3DList.add(new Point3(-halfMarkerSize, halfMarkerSize, 0));
//        point3DList.add(new Point3(halfMarkerSize, halfMarkerSize, 0));
//        point3DList.add(new Point3(halfMarkerSize, -halfMarkerSize, 0));
        point3DList.add(new Point3(-halfMarkerSize, -halfMarkerSize, markerSizeMeters));
        point3DList.add(new Point3(-halfMarkerSize, halfMarkerSize, markerSizeMeters));
        point3DList.add(new Point3(halfMarkerSize, halfMarkerSize, markerSizeMeters));
        point3DList.add(new Point3(halfMarkerSize, -halfMarkerSize, markerSizeMeters));

        objectPoints.fromList(point3DList);
    }

    private Scalar convertImageVectorToMotionVector(Scalar imageVector) {
        double pX = -imageVector.val[1];
        double pY = imageVector.val[0];
        double divisor = Math.sqrt((pX * pX) + (pY * pY));
        pX = pX / divisor;
        pY = pY / divisor;

        return new Scalar(pX * 0.4, pY * 0.4);
    }
}
