/**
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayr.drivemigration;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
//import android.support.v4.util.Pair;

import androidx.core.util.Pair;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility for performing read/write operations on Drive files via the REST API and opening a
 * file picker UI via Storage Access Framework.
 */
public class DriveServiceHelper {
    private static final String TAG ="DriveServiceHelper" ;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;
    private String AppFolderId =null; // Should create own folder which this would set to non-null
    private String  ParentDriveid=null;

    public DriveServiceHelper(Drive driveService) {
        mDriveService = driveService;
    }

    public boolean SetAppFolderId(final String AppFolderNameId){
        AppFolderId =AppFolderNameId;
        return true;
    }
    public Task<String> QueryorCreateAppFolder(final String AppFolderName)throws IOException {
        return Tasks.call(mExecutor, () -> {
            File AppFolderFile;

            //Query the folder if exists return its file id
            FileList result=mDriveService.files().list()
                    .setQ("name= '" +AppFolderName +"'")
                    .setFields("files(id, name)")
                    .execute();
            if (result.getFiles().size() >=1){
                AppFolderFile=result.getFiles().get(0);
                Log.d(TAG, " Found existing Appfolder: " + AppFolderFile.getName()+" Folderid:" + AppFolderFile.getId());
            }
            //if it does not then create one and return it's file id
            else {
                File metadata = new File()
                        .setName(AppFolderName)
                        .setMimeType("application/vnd.google-apps.folder");

                AppFolderFile = mDriveService.files().create(metadata)
                        .setFields("id")
                        .execute();
            }
            if ( AppFolderFile.getId() != null) {
                SetAppFolderId( AppFolderFile.getId());
            }
            else{
                throw new IOException("Cant Create Parent folder: " + AppFolderName);
            }
            return AppFolderFile.getId();
        });
    }
    public Task<String> CreateAppFolder  (final String AppFolderName) throws IOException {
        return Tasks.call(mExecutor, () -> {
            try {
                FileList result=mDriveService.files().list()
                        .setQ("name=' +AppFolderName+ ' ")
                        .setFields("files(id, name)")
                        .execute();
                if (result.getFiles() != null){
                    for (File file : result.getFiles()) {
                        Log.d(TAG, " Found file: " + file.getName()+" Folderid:" + file.getId());
                        ParentDriveid=file.getId();
                        SetAppFolderId(ParentDriveid);
                    }
                }
                else{
                    File metadata =new File()
                            .setName(AppFolderName)
                            .setMimeType("application/vnd.google-apps.folder");
                    File file = mDriveService.files().create(metadata)
                            .setFields("id")
                            .execute();
                    ParentDriveid=file.getId();
                    Log.d(TAG, " Created App Folder: " + file.getName()+" Folderid:" + file.getId());
                    SetAppFolderId(ParentDriveid);

                    if (file == null) {
                        throw new IOException("Cant Create Parent folder: " + AppFolderName);
                    }
                }
            } catch (IOException e) {
                throw new IOException("Cant  Query Parent folder: " + AppFolderName);
            }
            return ParentDriveid;
        });
    }

    public Task<String> SavetoAppFolder(final int practice_key, final String mFilename_to_Save) throws IOException {
        return Tasks.call(mExecutor, () -> {
            final java.io.File file_to_copy = new java.io.File(mFilename_to_Save);
            final String mFilename_short = file_to_copy.getName();
            String FileId=null;
            File metadata =new File()
                    .setMimeType("audio/mp4")
                    .setName(mFilename_short);
            if (AppFolderId != null){
                metadata.setParents(Collections.singletonList(AppFolderId));
            }
            FileContent mediaContent = new FileContent("audio/mp4", file_to_copy);
            File googleFile = mDriveService.files()
                    .create(metadata,mediaContent)
                    .setFields("id, parents").execute();
            Log.d(TAG, " Saving to  Parent AppFolderId Id: " + googleFile.getParents().toString()+" Appfolder: " + AppFolderId);
            return googleFile.getId();
        });
    }

    public Task<Void>  readfromAppfolder(final String fileId) {
        return Tasks.call(mExecutor, () -> {
            // Retrieve the metadata as a File object.
            File metadata = mDriveService.files().get(fileId).execute();
            String name = metadata.getName();

            //Create Outfile Locally
            String recFileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
            recFileName += "/AmpStudio";
            recFileName += "/Retrieved_cloudfile" + name+".mp4";
            FileOutputStream outputStream = new FileOutputStream(recFileName);

            //OutputStream outputStream = new ByteArrayOutputStream();
            mDriveService.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream);
            return null;
        });
    }

    /**
     * Creates a text file in the user's My Drive folder and returns its file ID.
     */
    public Task<String> createFile() {
        return Tasks.call(mExecutor, () -> {
                File metadata = new File()
                        .setParents(Collections.singletonList("root"))
                        .setMimeType("text/plain")
                        .setName("Untitled file");

                File googleFile = mDriveService.files().create(metadata).execute();
                if (googleFile == null) {
                    throw new IOException("Null result when requesting file creation.");
                }

                return googleFile.getId();
            });
    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
    public Task<Pair<String, String>> readFile(String fileId) {
        return Tasks.call(mExecutor, () -> {
                // Retrieve the metadata as a File object.
                File metadata = mDriveService.files().get(fileId).execute();
                String name = metadata.getName();

                // Stream the file contents to a String.
                try (InputStream is = mDriveService.files().get(fileId).executeMediaAsInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    String contents = stringBuilder.toString();

                    return Pair.create(name, contents);
                }
            });
    }

    /**
     * Updates the file identified by {@code fileId} with the given {@code name} and {@code
     * content}.
     */
    public Task<Void> saveFile(String fileId, String name, String content) {
        return Tasks.call(mExecutor, () -> {
                // Create a File containing any metadata changes.
                File metadata = new File().setName(name);

                // Convert content to an AbstractInputStreamContent instance.
                ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);

                // Update the metadata and contents.
                mDriveService.files().update(fileId, metadata, contentStream).execute();
                return null;
            });
    }

    /**
     * Returns a {@link FileList} containing all the visible files in the user's My Drive.
     *
     * <p>The returned list will only contain files visible to this app, i.e. those which were
     * created by this app. To perform operations on files not created by the app, the project must
     * request Drive Full Scope in the <a href="https://play.google.com/apps/publish">Google
     * Developer's Console</a> and be submitted to Google for verification.</p>
     */
    public Task<FileList> queryFiles() {
        return Tasks.call(mExecutor, () ->
                mDriveService.files().list().setSpaces("drive").execute());
    }

    /**
     * Returns an {@link Intent} for opening the Storage Access Framework file picker.
     */
    public Intent createFilePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");

        return intent;
    }

    /**
     * Opens the file at the {@code uri} returned by a Storage Access Framework {@link Intent}
     * created by {@link #createFilePickerIntent()} using the given {@code contentResolver}.
     */
    public Task<Pair<String, String>> openFileUsingStorageAccessFramework(
            ContentResolver contentResolver, Uri uri) {
        return Tasks.call(mExecutor, () -> {
                // Retrieve the document's display name from its metadata.
                String name;
                try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        name = cursor.getString(nameIndex);
                    } else {
                        throw new IOException("Empty cursor returned for file.");
                    }
                }

                // Read the document's contents as a String.
                String content;
                try (InputStream is = contentResolver.openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    content = stringBuilder.toString();
                }

                return Pair.create(name, content);
            });
    }
}
