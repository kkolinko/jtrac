package info.jtrac.domain;

import java.io.InputStream;

public class UploadedFile {
	public final String clientFilename;
	public final InputStream inputStream;
	public UploadedFile(String clientFilename, InputStream inputStream) {
		this.clientFilename = clientFilename;
		this.inputStream = inputStream;
	}
}