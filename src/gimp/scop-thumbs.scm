(define (scop-thumbs filename small-filename medium-filename)
  (let* ((org-image (car (gimp-file-load RUN-NONINTERACTIVE
				     filename filename)))
	 (drawable (car (gimp-image-get-active-layer org-image))))
	     
    (plug-in-autocrop RUN-NONINTERACTIVE
		      org-image drawable)

    (let* ((org-width  (car (gimp-image-width  org-image)))
	   (org-height (car (gimp-image-height org-image))))

      (let* ((image (car (gimp-image-duplicate org-image)))
	     (drawable (car (gimp-image-get-active-drawable image)))
	     (height 100)
	     (width (max 1 (* org-width (/ height org-height)))))
      
	(gimp-image-scale image width height)

	(gimp-file-save RUN-NONINTERACTIVE
			image drawable small-filename small-filename))

      (let* ((image (car (gimp-image-duplicate org-image)))
	     (drawable (car (gimp-image-get-active-drawable image)))
	     (height 500)
	     (width (max 1 (* org-width (/ height org-height)))))
      
	(gimp-image-scale image width height)

	(gimp-file-save RUN-NONINTERACTIVE
			image drawable medium-filename medium-filename))

      (gimp-image-delete org-image))))