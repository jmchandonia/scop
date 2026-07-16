(define (scop-selected-layer image)
  (if (defined? 'gimp-image-get-selected-layers)
      (vector-ref (car (gimp-image-get-selected-layers image)) 0)
      (car (gimp-image-get-active-layer image))))

(define (scop-selected-drawable image)
  (if (defined? 'gimp-image-get-selected-drawables)
      (vector-ref (car (gimp-image-get-selected-drawables image)) 0)
      (car (gimp-image-get-active-drawable image))))

(define (scop-image-width image)
  (if (defined? 'gimp-image-get-width)
      (car (gimp-image-get-width image))
      (car (gimp-image-width image))))

(define (scop-image-height image)
  (if (defined? 'gimp-image-get-height)
      (car (gimp-image-get-height image))
      (car (gimp-image-height image))))

(define (scop-autocrop image drawable)
  (if (defined? 'gimp-image-autocrop)
      (gimp-image-autocrop image drawable)
      (plug-in-autocrop RUN-NONINTERACTIVE image drawable)))

(define (scop-file-save image drawable filename)
  (if (defined? 'gimp-image-get-width)
      (gimp-file-save RUN-NONINTERACTIVE image filename)
      (gimp-file-save RUN-NONINTERACTIVE image drawable filename filename)))

(define (scop-thumb-tiny filename tiny-filename)
  (let* ((org-image (car (gimp-file-load RUN-NONINTERACTIVE
                                     filename filename)))
         (drawable (scop-selected-layer org-image)))

    (scop-autocrop org-image drawable)

    (let* ((org-width  (scop-image-width org-image))
           (org-height (scop-image-height org-image))
           (image (car (gimp-image-duplicate org-image)))
           (drawable (scop-selected-drawable image)))

      (if (< (/ org-width org-height) 1.8)
          (let* ((height 20)
                 (width (max 1 (* org-width (/ height org-height)))))
            (gimp-image-scale image width height))
          (let* ((width 36)
                 (height (max 1 (* org-height (/ width org-width)))))
            (gimp-image-scale image width height)))

      (scop-file-save image drawable tiny-filename)
      (gimp-image-delete image))

    (gimp-image-delete org-image)))
