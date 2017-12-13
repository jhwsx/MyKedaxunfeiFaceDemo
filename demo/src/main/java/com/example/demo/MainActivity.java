package com.example.demo;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.IdentityListener;
import com.iflytek.cloud.IdentityResult;
import com.iflytek.cloud.IdentityVerifier;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Camera mCamera;
    private SurfaceHolder mSurfaceHolder;
    private Context mContext;
    private IdentityVerifier mIdVerifier;
    private ProgressDialog mProDialog;
    private ImageView mImageView;

    // 模型操作
    private int mModelCmd;
    // 删除模型
    private final static int MODEL_DEL = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        initViews();
        initXunfeiIdentityVerifier();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 初始化Camera
        if (checkCameraHardware(this) && (mCamera == null)) {
            mCamera = getCamera();
            if (mSurfaceHolder != null && mCamera != null) {
                setStartPreview(mCamera, mSurfaceHolder);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setStartPreview(mCamera, holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // 重启功能
        mCamera.stopPreview();
        setStartPreview(mCamera, holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    public void finish() {
        if (null != mProDialog) {
            mProDialog.dismiss();
        }
        super.finish();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_register:
                enroll();
                break;
            case R.id.button_verify:
                verify();
                break;
            case R.id.button_delete:
                delete();
                break;
            default:
                break;
        }
    }

    private void delete() {
        // 人脸模型删除
        mModelCmd = MODEL_DEL;
        executeModelCommand("delete");
    }

    private int mOperationType;
    private final static int OPERATION_ENROLL = 0;
    private final static int OPERATION_VERIFY = 1;
    private void enroll() {
        mOperationType = OPERATION_ENROLL;
        capture();
    }

    private void capture() {
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureFormat(ImageFormat.JPEG);
//        params.setPreviewSize(400, 400);
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        mCamera.setParameters(params);
        if (safeToTakePicture) {
            mCamera.takePicture(null, null, mPictureCallback);
            safeToTakePicture = false;
        }
    }

    private void verify() {
        mOperationType = OPERATION_VERIFY;
        capture();
    }
    private void executeModelCommand(String cmd) {
        // 设置人脸模型操作参数
        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, getResources().getString(R.string.app_id));

        // 设置模型参数，若无可以传空字符传
        StringBuffer params = new StringBuffer();
        // 执行模型操作
        mIdVerifier.execute("ifr", cmd, params.toString(), mModelListener);
    }

    /**
     * 人脸模型操作监听器
     */
    private IdentityListener mModelListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            JSONObject jsonResult = null;
            int ret = ErrorCode.SUCCESS;
            try {
                jsonResult = new JSONObject(result.getResultString());
                ret = jsonResult.getInt("ret");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // 根据操作类型判断结果类型
            switch (mModelCmd) {
                case MODEL_DEL:
                    if (ErrorCode.SUCCESS == ret) {
                        Toast.makeText(mContext, "删除成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(mContext, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            // 弹出错误信息
            Toast.makeText(mContext, error.getPlainDescription(true), Toast.LENGTH_SHORT).show();
        }

    };

    private void initXunfeiIdentityVerifier() {
        // 初始化讯飞人脸识别引擎
        mIdVerifier = IdentityVerifier.createVerifier(mContext, new InitListener() {
            @Override
            public void onInit(int errorCode) {
                if (ErrorCode.SUCCESS == errorCode) {
                    Toast.makeText(mContext, "引擎初始化成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "引擎初始化失败，错误码：", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private byte[] mImageData;
    /**
     * Camera回调，通过data[]保持图片数据信息
     */
    Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            camera.startPreview();
            if (handlePicture(data)){
                return;
            }
            if (mOperationType == OPERATION_ENROLL) {
                goEnroll();
            } else if (mOperationType == OPERATION_VERIFY) {
                goVerify();
            }
            safeToTakePicture = true;
        }
    };

    private boolean handlePicture(byte[] data) {
        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            return true;
        }
        // 1, 保存照片到sd卡上
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(pictureFile);
            fileOutputStream.write(data);
            fileOutputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // 2, 获取图片保存路径
        String fileSrc = pictureFile.getAbsolutePath();
        // 3, 压缩图片
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fileSrc, options);
        options.inSampleSize = Math.max(1, (int) Math.ceil(Math.max(
                (double) options.outWidth / 1024f,
                (double) options.outHeight / 1024f)));
        options.inJustDecodeBounds = false;
        Bitmap image = BitmapFactory.decodeFile(fileSrc, options);
        // 若mImageBitmap为空则图片信息不能正常获取
        if (null == image) {
            Toast.makeText(mContext, "图片信息无法正常获取！", Toast.LENGTH_SHORT).show();
            return true;
        }
        // 4, 摆正图片
        image = rotateBitmap(image);
        mImageView.setImageBitmap(image);
        // 5, 传递图片给讯飞
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        //可根据流量及网络状况对图片进行压缩
        image.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        mImageData = baos.toByteArray();
        return false;
    }

    private void goVerify() {
        if (null != mImageData) {
            mProDialog.setMessage("验证中...");
            mProDialog.show();
            // 设置人脸验证参数
            // 清空参数
            mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
            // 设置会话场景
            mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
            // 设置会话类型
            mIdVerifier.setParameter(SpeechConstant.MFV_SST, "verify");
            // 设置验证模式，单一验证模式：sin
            mIdVerifier.setParameter(SpeechConstant.MFV_VCM, "sin");
            // 用户id
            mIdVerifier.setParameter(SpeechConstant.AUTH_ID, getResources().getString(R.string.app_id));
            // 设置监听器，开始会话
            mIdVerifier.startWorking(mVerifyListener);

            // 子业务执行参数，若无可以传空字符传
            StringBuffer params = new StringBuffer();
            // 向子业务写入数据，人脸数据可以一次写入
            mIdVerifier.writeData("ifr", params.toString(), mImageData, 0, mImageData.length);
            // 停止写入
            mIdVerifier.stopWrite("ifr");
        } else {
            Toast.makeText(mContext, "请拍照后再验证", Toast.LENGTH_SHORT).show();
        }
    }

    private void goEnroll() {
        if (null != mImageData) {
            mProDialog.setMessage("注册中...");
            mProDialog.show();
            // 设置用户标识，格式为6-18个字符（由字母、数字、下划线组成，不得以数字开头，不能包含空格）。
            // 当不设置时，云端将使用用户设备的设备ID来标识终端用户。
            // 设置人脸注册参数
            // 清空参数
            mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
            // 设置会话场景
            mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ifr");
            // 设置会话类型
            mIdVerifier.setParameter(SpeechConstant.MFV_SST, "enroll");
            // 设置用户id
            mIdVerifier.setParameter(SpeechConstant.AUTH_ID, getResources().getString(R.string.app_id));
            // 设置监听器，开始会话
            mIdVerifier.startWorking(mEnrollListener);

            // 子业务执行参数，若无可以传空字符传
            StringBuffer params = new StringBuffer();
            // 向子业务写入数据，人脸数据可以一次写入
            mIdVerifier.writeData("ifr", params.toString(), mImageData, 0, mImageData.length);
            // 停止写入
            mIdVerifier.stopWrite("ifr");
        } else {
            Toast.makeText(mContext, "请拍照后再注册", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取图片保持路径
     *
     * @return pic Path
     */
    private File getOutputMediaFile() {
        File mediaStorageDir = new File(MainActivity.this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MyCameraApp");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }
        return new File(mediaStorageDir.getPath() + File.separator + "temp.png");
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {

        Matrix rotateRight = new Matrix();
        rotateRight.preRotate(90);

        float[] mirrorY = {-1, 0, 0, 0, 1, 0, 0, 0, 1};
        rotateRight = new Matrix();
        Matrix matrixMirrorY = new Matrix();
        matrixMirrorY.setValues(mirrorY);

        rotateRight.postConcat(matrixMirrorY);

        rotateRight.preRotate(270);


        return Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), rotateRight, true);
    }

    private int findFrontCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras(); // get cameras number

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                // 代表摄像头的方位，目前有定义值两个分别为CAMERA_FACING_FRONT前置和CAMERA_FACING_BACK后置
                return camIdx;
            }
        }
        return -1;
    }

    private void initViews() {
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceHolder = surfaceView.getHolder(); // 获取SurfaceView的holder，起获取和控制surface的作用
        mSurfaceHolder.addCallback(this); // 给SurfaceHolder设置回调接口

        Button btnRegister = (Button) findViewById(R.id.button_register);
        mImageView = (ImageView) findViewById(R.id.imageView);
        Button btnVerify = (Button) findViewById(R.id.button_verify);
        Button btnDelete = (Button) findViewById(R.id.button_delete);
        btnRegister.setOnClickListener(this);
        btnVerify.setOnClickListener(this);
        btnDelete.setOnClickListener(this);

        mProDialog = new ProgressDialog(this);
        mProDialog.setCancelable(true);
        mProDialog.setTitle("请稍后");
        mProDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // cancel进度框时,取消正在进行的操作
                if (null != mIdVerifier) {
                    mIdVerifier.cancel();
                }
            }
        });
    }

    /**
     * 人脸注册监听器
     */
    private IdentityListener mEnrollListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            if (null != mProDialog) {
                mProDialog.dismiss();
            }

            try {
                JSONObject object = new JSONObject(result.getResultString());
                int ret = object.getInt("ret");

                if (ErrorCode.SUCCESS == ret) {
                    Toast.makeText(mContext, "注册成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, new SpeechError(ret).getPlainDescription(true), Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {}

        @Override
        public void onError(SpeechError error) {
            if (null != mProDialog) {
                mProDialog.dismiss();
            }
            Toast.makeText(mContext, error.getPlainDescription(true), Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * 人脸验证监听器
     */
    private IdentityListener mVerifyListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            if (null != mProDialog) {
                mProDialog.dismiss();
            }

            try {
                JSONObject object = new JSONObject(result.getResultString());
                Log.d(TAG,"object is: "+object.toString());
                String decision = object.getString("decision");

                if ("accepted".equalsIgnoreCase(decision)) {
                    Toast.makeText(mContext, "通过验证", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "验证失败", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            if (null != mProDialog) {
                mProDialog.dismiss();
            }
            Toast.makeText(mContext, error.getPlainDescription(true), Toast.LENGTH_SHORT).show();
        }

    };
    /**
     * 初始化相机
     *
     * @return camera
     */
    private Camera getCamera() {
        try {
            mCamera = Camera.open(findFrontCamera());
        } catch (Exception e) {
            mCamera = null;
        }
        return mCamera;
    }

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview(); // 停掉摄像头的预览
            mCamera.release(); // 释放资源
            mCamera = null;
        }
    }

    private boolean safeToTakePicture = false;

    /**
     * 在SurfaceView中预览相机内容
     *
     * @param camera camera
     * @param holder SurfaceHolder
     */
    private void setStartPreview(Camera camera, SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(holder);// 这个方法必须在startPreview之前调用
            camera.setDisplayOrientation(90);
            camera.startPreview();
            safeToTakePicture = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查是否有Camera功能
     *
     * @param context
     * @return
     */
    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

}
