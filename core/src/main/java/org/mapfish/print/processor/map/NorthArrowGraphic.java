package org.mapfish.print.processor.map;

import com.google.common.base.Strings;
import com.google.common.io.Closer;

import org.apache.batik.dom.svg.SAXSVGDocumentFactory;
import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.dom.util.DOMUtilities;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.XMLResourceDescriptor;
import org.mapfish.print.config.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGElement;

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class NorthArrowGraphic {

    private static final String SVG_NS = SVGDOMImplementation.SVG_NAMESPACE_URI;

    private NorthArrowGraphic() { }

    public static URI create(
            final Dimension targetSize,
            final String graphicFile,
            final Double rotation,
            final File workingDir,
            final Configuration configuration,
            final ClientHttpRequestFactory clientHttpRequestFactory) throws Exception {
        final Closer closer = Closer.create();
        try {
            final InputStream input = loadGraphic(graphicFile, configuration, clientHttpRequestFactory, closer);
            if (graphicFile.toLowerCase().trim().endsWith("svg")) {
                return createSvg(targetSize, input, rotation, workingDir, clientHttpRequestFactory);
            } else {
                return createRaster(targetSize, input, rotation, workingDir, clientHttpRequestFactory);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            closer.close();
        }
    }

    private static InputStream loadGraphic(final String graphicFile,
            final Configuration configuration,
            final ClientHttpRequestFactory clientHttpRequestFactory,
            final Closer closer) throws IOException, URISyntaxException {
        final InputStream input;
        if (configuration != null && configuration.isAccessible(graphicFile)) {
            // load graphic from configuration directory
            input = new ByteArrayInputStream(configuration.loadFile(graphicFile));
        } else {
            // load graphic from URL
            final URI uri = new URI(graphicFile);
            final ClientHttpRequest request = clientHttpRequestFactory.createRequest(uri, HttpMethod.GET);
            final ClientHttpResponse response = closer.register(request.execute());
            input = new BufferedInputStream(response.getBody());
        }
        return input;
    }

    private static URI createRaster(final Dimension targetSize, final InputStream inputStream,
            final Double rotation, final File workingDir, final ClientHttpRequestFactory clientHttpRequestFactory) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * With the Batik SVG library it is only possible to create new SVG graphics,
     * but you can not modify an existing graphic. So, we are loading the SVG file
     * as plain XML and doing the modifications by hand.
     */
    private static URI createSvg(final Dimension targetSize,
            final InputStream inputStream, final Double rotation,
            final File workingDir,
            final ClientHttpRequestFactory clientHttpRequestFactory)
            throws IOException {
        // load SVG graphic
        final SVGElement svgRoot = parseSvg(inputStream);

        // create a new SVG graphic in which the existing graphic is embedded (scaled and rotated)
        DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
        Document newDocument = impl.createDocument(SVG_NS, "svg", null);
        SVGElement newSvgRoot = (SVGElement) newDocument.getDocumentElement();
        newSvgRoot.setAttributeNS(null, "width", Integer.toString(targetSize.width));
        newSvgRoot.setAttributeNS(null, "height", Integer.toString(targetSize.height));

        embedSvgGraphic(svgRoot, newSvgRoot, newDocument, targetSize, rotation);
        File path = writeSvgToFile(newDocument, workingDir);

        return path.toURI();
    }

    /**
     * Embeds the given SVG element into a new SVG element scaling
     * the graphic to the given dimension and applying the given
     * rotation.
     */
    private static void embedSvgGraphic(final SVGElement svgRoot,
            final SVGElement newSvgRoot, final Document newDocument,
            final Dimension targetSize, final Double rotation) {
        final String originalWidth = svgRoot.getAttributeNS(null, "width");
        final String originalHeight = svgRoot.getAttributeNS(null, "height");
        /*
         * To scale the SVG graphic and to apply the rotation, we distinguish two
         * cases: width and height is set on the original SVG or not.
         *
         * Case 1: Width and height is set
         * If width and height is set, we wrap the original SVG into 2 new SVG elements
         * and a container element.
         *
         * Example:
         *      Original SVG:
         *          <svg width="100" height="100"></svg>
         *
         *      New SVG (scaled to 300x300 and rotated by 90 degree):
         *          <svg width="300" height="300">
         *              <g transform="rotate(90.0 150 150)">
         *                  <svg width="100%" height="100%" viewBox="0 0 100 100">
         *                      <svg width="100" height="100"></svg>
         *                  </svg>
         *              </g>
         *          </svg>
         *
         * The requested size is set on the outermost <svg>. Then, the rotation is applied to the
         * <g> container and the scaling is achieved with the viewBox parameter on the 2nd <svg>.
         *
         *
         * Case 2: Width and height is not set
         * In this case the original SVG is wrapped into just one container and one new SVG element.
         * The rotation is set on the container, and the scaling happens automatically.
         *
         * Example:
         *      Original SVG:
         *          <svg viewBox="0 0 61.06 91.83"></svg>
         *
         *      New SVG (scaled to 300x300 and rotated by 90 degree):
         *          <svg width="300" height="300">
         *              <g transform="rotate(90.0 150 150)">
         *                  <svg viewBox="0 0 61.06 91.83"></svg>
         *              </g>
         *          </svg>
         */
        if (!Strings.isNullOrEmpty(originalWidth) && !Strings.isNullOrEmpty(originalHeight)) {
            Element wrapperContainer = newDocument.createElementNS(SVG_NS, "g");
            wrapperContainer.setAttributeNS(
                    null,
                    SVGConstants.SVG_TRANSFORM_ATTRIBUTE,
                    getRotateTransformation(targetSize, rotation));
            newSvgRoot.appendChild(wrapperContainer);

            Element wrapperSvg = newDocument.createElementNS(SVG_NS, "svg");
            wrapperSvg.setAttributeNS(null, "width", "100%");
            wrapperSvg.setAttributeNS(null, "height", "100%");
            wrapperSvg.setAttributeNS(null, "viewBox", "0 0 " + originalWidth
                    + " " + originalHeight);
            wrapperContainer.appendChild(wrapperSvg);

            Node svgRootImported = newDocument.importNode(svgRoot, true);
            wrapperSvg.appendChild(svgRootImported);
        } else {
            Element wrapperContainer = newDocument.createElementNS(SVG_NS, "g");
            wrapperContainer.setAttributeNS(
                    null,
                    SVGConstants.SVG_TRANSFORM_ATTRIBUTE,
                    getRotateTransformation(targetSize, rotation));
            newSvgRoot.appendChild(wrapperContainer);

            Node svgRootImported = newDocument.importNode(svgRoot, true);
            wrapperContainer.appendChild(svgRootImported);
        }
    }

    private static String getRotateTransformation(final Dimension targetSize,
            final double rotation) {
        return "rotate(" + Double.toString(rotation) + " "
                + Integer.toString(targetSize.width / 2) + " "
                + Integer.toString(targetSize.height / 2) + ")";
    }

    private static SVGElement parseSvg(final InputStream inputStream)
            throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
        SVGDocument document = (SVGDocument) f.createDocument("", inputStream);
        return (SVGElement) document.getDocumentElement();
    }

    private static File writeSvgToFile(final Document document,
            final File workingDir) throws IOException {
        final File path = File.createTempFile("north-arrow-", ".svg", workingDir);
        FileWriter fw = null;
        try {
            fw = new FileWriter(path);
            DOMUtilities.writeDocument(document, fw);
            fw.flush();
        } finally {
            if (fw != null) {
                fw.close();
            }
        }
        return path;
    }

}
