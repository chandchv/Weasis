/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;

public class LoadRemoteDicomURL extends SwingWorker<Boolean, String> {

    private final URL[] urls;
    private final DicomModel dicomModel;

    public LoadRemoteDicomURL(String[] urls, DataExplorerModel explorerModel) {
        if (urls == null || !(explorerModel instanceof DicomModel))
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        URL[] urlRef = new URL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                try {
                    urlRef[i] = new URL(urls[i]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        this.urls = urlRef;
        this.dicomModel = (DicomModel) explorerModel;
    }

    public LoadRemoteDicomURL(URL[] urls, DataExplorerModel explorerModel) {
        if (urls == null || !(explorerModel instanceof DicomModel))
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        this.urls = urls;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        String seriesUID = null;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                seriesUID = urls[i].toString();
                break;
            }
        }
        if (seriesUID != null) {
            String unknown = Messages.getString("DownloadManager.unknown");//$NON-NLS-1$
            MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, unknown);
            if (patient == null) {
                patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, unknown, TagW.PatientName);
                patient.setTag(TagW.PatientID, unknown);
                patient.setTag(TagW.PatientName, unknown);
                dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
            }
            MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, unknown);
            if (study == null) {
                study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, unknown, TagW.StudyDate);
                dicomModel.addHierarchyNode(patient, study);
            }
            Series dicomSeries = dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
            dicomSeries.setTag(TagW.SeriesInstanceUID, seriesUID);
            dicomSeries.setTag(TagW.WadoParameters, new WadoParameters("", false, "", null, null));
            List<DicomInstance> dicomInstances = new ArrayList<DicomInstance>();
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, dicomInstances);
            dicomModel.addHierarchyNode(study, dicomSeries);

            LoadSeries s = new LoadSeries(dicomSeries, dicomModel);

            for (int i = 0; i < urls.length; i++) {
                if (urls[i] != null) {
                    String url = urls[i].toString();
                    DicomInstance dcmInstance = new DicomInstance(url, null);
                    dcmInstance.setDirectDownloadFile(url);
                    dicomInstances.add(dcmInstance);
                }
            }
            LoadRemoteDicomManifest.loadingQueue.offer(s);
            Runnable[] tasks =
                LoadRemoteDicomManifest.loadingQueue.toArray(new Runnable[LoadRemoteDicomManifest.loadingQueue.size()]);
            for (int i = 0; i < tasks.length; i++) {
                LoadRemoteDicomManifest.currentTasks.add((LoadSeries) tasks[i]);
            }
            LoadRemoteDicomManifest.executor.prestartAllCoreThreads();
        }
        return true;
    }

    @Override
    protected void done() {
    }

}