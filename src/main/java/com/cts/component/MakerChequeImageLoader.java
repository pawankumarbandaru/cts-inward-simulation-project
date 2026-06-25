package com.cts.component;

/**
 * File    : MakerChequeImageLoader.java
 * Package : com.cts.component
 * Purpose : Loads the front and back cheque images into the repair workspace.
 *           Shows the real image when a URL is present; shows a CSS placeholder otherwise.
 *           Front and back sides are handled independently.
 * Author  : Ramana
 * Date    : 24-06-2025
 */

import org.zkoss.zul.Div;
import org.zkoss.zul.Image;

import com.cts.inward.entity.InwardCheque;

public class MakerChequeImageLoader {

    private final Image realFrontImg;
    private final Image realBackImg;
    private final Div   realImgWrapper;
    private final Div   imgPlaceholder;
    private final Div   realBackWrapper;
    private final Div   backPlaceholder;

    /**
     * @param realFrontImg    ZK Image wired to #chq-real-front
     * @param realBackImg     ZK Image wired to #chq-real-back
     * @param realImgWrapper  ZK Div  wired to #chq-real-wrapper
     * @param imgPlaceholder  ZK Div  wired to #chq-img-placeholder
     * @param realBackWrapper ZK Div  wired to #chq-real-back-wrapper
     * @param backPlaceholder ZK Div  wired to #chq-back-placeholder
     */
    public MakerChequeImageLoader(Image realFrontImg, Image realBackImg,
                                   Div realImgWrapper, Div imgPlaceholder,
                                   Div realBackWrapper, Div backPlaceholder) {
        this.realFrontImg    = realFrontImg;
        this.realBackImg     = realBackImg;
        this.realImgWrapper  = realImgWrapper;
        this.imgPlaceholder  = imgPlaceholder;
        this.realBackWrapper = realBackWrapper;
        this.backPlaceholder = backPlaceholder;
    }

    /**
     * Loads front and back images from the InwardCheque entity.
     * Each side (front/back) is handled independently — a missing back image
     * does not affect the front display.
     *
     * @param cheque the cheque whose images should be loaded
     */
    public void load(InwardCheque cheque) {
        if (cheque == null) {
            showFrontPlaceholder();
            showBackPlaceholder();
            return;
        }

        try {
            String frontUrl = cheque.getFrontImagePath();
            String backUrl  = cheque.getRearImagePath();

            boolean hasFront = (frontUrl != null && !frontUrl.isBlank());
            boolean hasBack  = (backUrl  != null && !backUrl.isBlank());

            if (hasFront) {
                if (realFrontImg   != null) realFrontImg.setSrc(frontUrl);
                if (realImgWrapper != null) realImgWrapper.setVisible(true);
                if (imgPlaceholder != null) imgPlaceholder.setVisible(false);
            } else {
                showFrontPlaceholder();
            }

            if (hasBack) {
                if (realBackImg     != null) realBackImg.setSrc(backUrl);
                if (realBackWrapper != null) realBackWrapper.setVisible(true);
                if (backPlaceholder != null) backPlaceholder.setVisible(false);
            } else {
                showBackPlaceholder();
            }

        } catch (Exception ex) {
            System.err.println("MakerChequeImageLoader: failed to load images — " + ex.getMessage());
            showFrontPlaceholder();
            showBackPlaceholder();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void showFrontPlaceholder() {
        if (realImgWrapper != null) realImgWrapper.setVisible(false);
        if (imgPlaceholder != null) imgPlaceholder.setVisible(true);
    }

    private void showBackPlaceholder() {
        if (realBackWrapper != null) realBackWrapper.setVisible(false);
        if (backPlaceholder != null) backPlaceholder.setVisible(true);
    }
}