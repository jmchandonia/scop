(define (scop-thumb-tiny filename tiny-filename)
  (let* ((org-image (car (gimp-file-load RUN-NONINTERACTIVE
				     filename filename)))
	 (drawable (car (gimp-image-get-active-layer org-image))))
	     
    (plug-in-autocrop RUN-NONINTERACTIVE
		      org-image drawable)

    (let* ((org-width  (car (gimp-image-width  org-image)))
	   (org-height (car (gimp-image-height org-image)))
	   (image (car (gimp-image-duplicate org-image)))
	   (drawable (car (gimp-image-get-active-drawable image))))

      (if (< (/ org-width org-height) 1.8)
	  (let* ((height 20)
		 (width (max 1 (* org-width (/ height org-height)))))
	    (gimp-image-scale image width height))
      ;else 
	  (let* ((width 36)
		 (height (max 1 (* org-height (/ width org-width)))))
	    (gimp-image-scale image width height)))

      (gimp-file-save RUN-NONINTERACTIVE
		      image drawable tiny-filename tiny-filename))

    (gimp-image-delete org-image)))