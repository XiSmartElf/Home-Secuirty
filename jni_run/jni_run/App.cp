#include <jni.h>
#include <iostream>
#include "App.h"
#include <string>
#include <array>
#include <fstream>
#include <thread>
#include <time.h>
#include <queue>
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"
#include <unistd.h>

using namespace cv;
using namespace std;
using namespace std::chrono;




int maxCol;
int maxRow;
int minCol;
int minRow;
int changeThreshold = 20;
bool stopVision = false;
static condition_variable sceneDetected;
static mutex mtx;
mutex criticalSec;
static unique_lock<mutex> lock_detection(mtx);
//jpeg compression
vector<uchar> buff;//buffer for coding
vector<int> param = vector<int>(2);
mutex mtx2;
mutex buffLock;
unique_lock<mutex> vidLock(mtx2);
condition_variable buff_full;
string storePath="";
bool readyToSend = true; //count to really detect the scene, clear to 0 if server clears the warnning and then we can use to send the next image.


//******************************************************* Other Functions *******************************


// build current time
const string currentDateTime() {
    time_t     now = time(0);
    struct tm  tstruct;
    char       buf[80];
    tstruct = *localtime(&now);
    strftime(buf, sizeof(buf), "%Y-%m-%d.%X", &tstruct);
    return buf;
}

//search region area of moving object
void updateBound(int i, int j, Mat frame)
{
    if( i>=0 && j>=0 && i<frame.rows && j < frame.cols)
    {
        if(frame.at<uchar>(i,j)==255)
        {
            //if a 255 is found, update max, min value of col and row
            maxCol = maxCol< j? j : maxCol;
            maxRow = maxRow< i? i : maxRow;
            minCol = minCol> j? j : minCol;
            minRow = minRow> i? i : minRow;
        }
    }

}

void search(int x, int y, int m, Mat frame)
{
    //x, y is the center position(moment), m determines the search square size
    int tempMaxCol = maxCol;
    int tempMaxRow = maxRow;;
    int tempMinCol = minCol;
    int tempMinRow = minRow;;
    
    // x - m --> x + m  @  ( y + m )
    for(int j = x-m; j< x+m; j++)
        updateBound(y+m, j, frame);
    // x - m --> x + m  @  ( y - m )
    for(int j = x-m; j< x+m; j++)
        updateBound(y-m, j, frame);
    // y + m --> y - m  @  ( x - m )
    for(int i = y+m; i> y-m; i--)
        updateBound(i, x-m, frame);
    // y + m --> y - m  @  ( x + m )
    for(int i = y+m; i> y-m; i--)
        updateBound(i, x+m, frame);

    if( tempMaxCol != maxCol || tempMaxRow !=maxRow || tempMinCol != minCol || tempMinRow !=minRow )
        search(x, y, m+1, frame); //recursive to search with m+1
}


//****************************  primary functions *********************


int startProcessing(String vdFileName, VideoCapture cap)
{
    //Define where the output will be stored at
    char buffer[200];
    char *answer = getcwd(buffer, sizeof(buffer));
    string s_cwd;
    String videoOut = "";
    String imageFolder = "";
    if (answer){
        s_cwd = answer;
        videoOut = String(s_cwd) + "/" + vdFileName;
        imageFolder = String(answer)+"/sceneImages/";
        cout<<"scene video location: "<<videoOut<<endl;
        cout<<"scene image location: "<<imageFolder<<endl;
    }
    if ( !cap.isOpened() )  // if not success, exit program
    {
        cout << "Cannot open the web cam/video file: "<< videoOut << endl;
        return -1;
    }
    
    double dWidth = cap.get(CV_CAP_PROP_FRAME_WIDTH); //get the width of frames of the video
    double dHeight = cap.get(CV_CAP_PROP_FRAME_HEIGHT); //get the height of frames of the video
    Size frameSize(static_cast<int>(dWidth), static_cast<int>(dHeight));
    cout<<"camera width: "<<dWidth<<" . camera height: "<<dHeight<<endl;

    Size size(384,288);
    param[0]=CV_IMWRITE_JPEG_QUALITY;
    param[1]=95;//default(95) 0-100
    
    VideoWriter oVideoWriter (videoOut, CV_FOURCC('M', 'P', 'E', 'G'), 14, frameSize, true); //initialize the VideoWriter object
    
    if ( !oVideoWriter.isOpened() ) //if not initialize the VideoWriter successfully, exit the program
    {
        cout << "ERROR: Failed to write the video of "<< vdFileName<< endl;
        return -1;
    }
    

//******************************* frame loop/ For each frame of the input source **************************
    
    //initialize some parameters for change detection
    bool first =true;
    Mat previousFrame;
    Mat currentImage;
    Mat prevImage;
    cap.read(prevImage);
    clock_t begin_time = clock();
    int scene_count=0;
    Mat imgTmp = prevImage;
    struct timeval tp;
    
    while (true)
    {
        long startTime = (long long) tp.tv_sec * 1000L + tp.tv_usec / 1000;
        Mat imgOriginal;
        Mat luma(imgTmp.size(), CV_8UC1);
        Mat change = luma.clone();
        Mat change_4secs = Mat::zeros( imgTmp.size(), CV_8UC1 );
        Mat imgLines = Mat::zeros(imgTmp.size(), CV_8UC3 );

        bool bSuccess = cap.read(imgOriginal); // read a new frame from the input source
        if (!bSuccess) {  //if not success, break loop
            cout << "Cannot read a frame from video stream" << endl;
            break;
        }
        
        //convert to greyscale image
        cvtColor(imgOriginal, luma, CV_RGB2GRAY);
    
        //check every 4 secs see if things changes
        if(float( clock () - begin_time ) /  CLOCKS_PER_SEC > 4)
        {
            for(int i=0; i<luma.rows; i++){
                for(int j=0; j<luma.cols; j++){
                    uchar intensity_now = luma.at<uchar>(i, j);
                    uchar intensity_previous = prevImage.at<uchar>(i, j);
                    if(abs(intensity_now-intensity_previous)>changeThreshold)
                        change_4secs.at<uchar>(i, j) = 255;
                    else
                        change_4secs.at<uchar>(i, j) = 0;
                }
            }
            
            //morphological opening (remove small objects from the foreground)
            erode(change_4secs, change_4secs, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            dilate( change_4secs, change_4secs, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            //morphological closing (fill small holes in the foreground)
            dilate( change_4secs, change_4secs, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            erode(change_4secs, change_4secs, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            
            //check the moment of the change
            Moments moments_4secs = moments(change_4secs);
            double dM01 = moments_4secs.m01;
            double dM10 = moments_4secs.m10;
            double dArea = moments_4secs.m00;
            int posX = dM10 / dArea;
            int posY = dM01 / dArea;

            if(dArea> 500000)
            {
                line(imgLines, Point(posX-10, posY+10), Point(posX+10, posY-10), Scalar(255,0,0), 6);
                line(imgLines, Point(posX-10, posY-10), Point(posX+10, posY+10), Scalar(255,0,0), 6);
                imgOriginal = imgOriginal + imgLines;
                criticalSec.lock();
                if(readyToSend == true){
                    string dates = currentDateTime();
                    storePath = imageFolder + dates + string(".png");
                    imwrite(storePath, imgOriginal);
                    readyToSend = false;
                    sceneDetected.notify_all();
                }
                criticalSec.unlock();
                cvtColor(change_4secs, change_4secs, COLOR_GRAY2BGR);
                imgOriginal = imgOriginal + change_4secs;

            }
            prevImage = luma;
            begin_time = clock();
        }
        else
        {
            //frame change detection
            if(first==true){
                previousFrame=luma;
                prevImage = luma;
                first=false;
            }
            else{
                for(int i=0; i<luma.rows; i++){
                    for(int j=0; j<luma.cols; j++){
                        uchar intensity_now = luma.at<uchar>(i, j);
                        uchar intensity_previous = previousFrame.at<uchar>(i, j);
                        if(abs(intensity_now-intensity_previous)>changeThreshold)
                            change.at<uchar>(i, j) = 255;
                        else
                            change.at<uchar>(i, j) = 0;
                    }
                }
                previousFrame=luma;
            }
            
            //morphological opening (remove small objects from the foreground)
            erode(change, change, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            dilate( change, change, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            //morphological closing (fill small holes in the foreground)
            dilate( change, change, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            erode(change, change, getStructuringElement(MORPH_ELLIPSE, Size(5, 5)) );
            
            //Calculate the moments of the thresholded image
            Moments oMoments = moments(change);
            double dM01 = oMoments.m01;
            double dM10 = oMoments.m10;
            double dArea = oMoments.m00;
            
            if (dArea > 500000)
            {
                //calculate the position of the hand
                int posX = dM10 / dArea;
                int posY = dM01 / dArea;
                //get hand square region
                Mat frame = change; //to search hand in ANDed frame
                maxRow=posY+100; maxCol=posX+100; minRow=posY-100; minCol=posX-100;
                //call the hand search function that runs the algorithm to get the hand region of interest
                search(posX,posY,150, frame);
                
                //construct a square line for presentation
                if ( posX >= 0 && posY >= 0)
                {
                    line(imgLines, Point(minCol, minRow), Point(maxCol, minRow), Scalar(0,255,0), 3);
                    line(imgLines, Point(minCol, minRow), Point(minCol, maxRow), Scalar(0,255,0), 3);
                    line(imgLines, Point(minCol, maxRow), Point(maxCol, maxRow), Scalar(0,255,0), 3);
                    line(imgLines, Point(maxCol, maxRow), Point(maxCol, minRow), Scalar(0,255,0), 3);
                    line(imgLines, Point(posX-10, posY+10), Point(posX+10, posY-10), Scalar(0,0,255), 3);
                    line(imgLines, Point(posX-10, posY-10), Point(posX+10, posY+10), Scalar(0,0,255), 3);
                    imgOriginal = imgOriginal + imgLines;
                    
                    scene_count++; //when things are really detected
                    criticalSec.lock();
                    if(scene_count > 5 &&  readyToSend ==true)
                    {
                        string dates = currentDateTime();
                        storePath = imageFolder+dates + string(".jpg");
                        imwrite(storePath, imgOriginal);
                        readyToSend = false;
                        scene_count=0;
                        sceneDetected.notify_all();
                    }
                    criticalSec.unlock();
                }
            }
            else
                scene_count=0;
        }
        
        buffLock.lock();
        buff.clear();
        Mat dst;
        resize(imgOriginal,dst,size);
        imencode(".jpg",dst,buff,param);
        buff_full.notify_all();
        buffLock.unlock();
        
        imshow("final", imgOriginal); //show the combined
        //sleep to make make real framerate
        long endTime = (long long) tp.tv_sec * 1000L + tp.tv_usec / 1000;
        if(endTime - startTime<50)
            usleep((int)(50-(endTime - startTime)));

        oVideoWriter.write(imgOriginal); //writer the segmentation into the video output
        
        if (stopVision == true) //wait for 'esc' key press for 30ms. If 'esc' key is pressed, break loop
        {
            oVideoWriter.release();
            stopVision = false;
            break;
        }
    }
    return 0;
}

//***************************************** handle JNI signal vision communication *******************************

JNIEXPORT void JNICALL Java_gcmServer_App_visionCap(JNIEnv *, jobject){

    VideoCapture cap(0); //capture the video from web cam
    thread vision(startProcessing,"homeVideoRecord.mpeg",cap);
    vision.join();
}

JNIEXPORT jstring JNICALL Java_gcmServer_App_signal(JNIEnv *env, jobject obj, jstring string) {
    
    const char *str = env->GetStringUTFChars(string, 0);
    char cap[128];
    strcpy(cap, str);
    String message =  String(cap);
    delete [] str;
    if(message == "terminateVision")
    {
        stopVision = true;
        return env->NewStringUTF("Vision is going to terminate......");
    }
    else if(message == "sceneCheck")
    {
        sceneDetected.wait_for(lock_detection, std::chrono::milliseconds(3*1000));
        char returnVal[128];
        if(storePath == ""){
            String returnStr = "Not found";
            for (int i=0;i<=returnStr.size();i++)
                returnVal[i]=returnStr[i];
            return env->NewStringUTF(returnVal);
        }
        else{
            for (int i=0;i<=storePath.size();i++)
                returnVal[i]=storePath[i];
            storePath="";
            return env->NewStringUTF(returnVal);
        }
    }
    else if(message == "warningClear")
    {
        criticalSec.lock();
        readyToSend = true;
        criticalSec.unlock();
        return env->NewStringUTF("warnning cleared. system keeps monitoring");
    }
    return env->NewStringUTF("request_Invalid");
}


JNIEXPORT jbyteArray JNICALL Java_gcmServer_App_frameObtain(JNIEnv *env, jobject obj) {
    
    buffLock.lock();
    while(buff.size()==0){
        buffLock.unlock();
        buff_full.wait(vidLock);
        buffLock.lock();
    }
    //convert vector<char> to jbyteArray
    jbyte* resultJByte = new jbyte[buff.size()];
    jbyteArray resultByteArray = env->NewByteArray((int)buff.size());
    for (int i = 0; i < buff.size(); i++)
        resultJByte[i] = (jbyte)buff[i];

    env->SetByteArrayRegion(resultByteArray, 0, (int)buff.size(), resultJByte);
    buffLock.unlock();
    delete[] resultJByte;
    return resultByteArray;

}



