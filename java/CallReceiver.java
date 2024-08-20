import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallReceiver extends BroadcastReceiver {

    private boolean isRecording = false;
    private MediaRecorder recorder;
    private String phoneNumber;

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        } else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
            startRecording(context);
        } else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
            if (isRecording) {
                stopRecording();
                sendRecordingToServer(context);
            }
        }
    }

    private void startRecording(Context context) {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(context.getExternalFilesDir(null).getAbsolutePath() + "/call_recording.3gp");

        try {
            recorder.prepare();
            recorder.start();
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        recorder.stop();
        recorder.release();
        isRecording = false;
    }

    private void sendRecordingToServer(Context context) {
        String filePath = context.getExternalFilesDir(null).getAbsolutePath() + "/call_recording.3gp";
        new Thread(() -> {
            try {
                File file = new File(filePath);
                FileInputStream fileInputStream = new FileInputStream(file);

                // 서버 URL 수정 (http:// 추가)
                HttpURLConnection connection = (HttpURLConnection) new URL("http://evision-phishing.kro.kr").openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=*****");

                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

                // 파일 전송
                outputStream.writeBytes("--*****\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
                outputStream.writeBytes("\r\n");

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.writeBytes("\r\n");

                // 전화번호 전송
                outputStream.writeBytes("--*****\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"phone_number\"\r\n");
                outputStream.writeBytes("\r\n");
                outputStream.writeBytes(phoneNumber + "\r\n");

                // Boundary 종료
                outputStream.writeBytes("--*****--\r\n");
                outputStream.flush();
                outputStream.close();
                fileInputStream.close();

                // 서버 응답 확인
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 서버로의 전송 성공
                    InputStream responseStream = new BufferedInputStream(connection.getInputStream());
                    BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));
                    StringBuilder responseStringBuilder = new StringBuilder();
                    String line;
                    while ((line = responseStreamReader.readLine()) != null) {
                        responseStringBuilder.append(line).append("\n");
                    }
                    responseStreamReader.close();
                    String response = responseStringBuilder.toString();

                    // 서버 응답 처리
                    handleServerResponse(context, response);
                } else {
                    Log.e("Server Response", "Error: " + responseCode);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleServerResponse(Context context, String responseData) {
        try {
            JSONObject jsonResponse = new JSONObject(responseData);
            String transcript = jsonResponse.getString("transcript");
            String summary = jsonResponse.getString("summary");
            String warningMessage = jsonResponse.optString("warning_message", "");

            Log.d("Server Response", "Transcript: " + transcript);
            Log.d("Server Response", "Summary: " + summary);

            // warningMessage가 비어 있지 않으면 경고창 띄우기
            if (!warningMessage.isEmpty()) {
                showAlert(context, warningMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Context context, String message) {
        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .create()
                .show();
    }
}
