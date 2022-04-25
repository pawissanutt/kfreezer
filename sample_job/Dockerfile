FROM ubuntu:focal
RUN apt update && \
    apt install -y python3 curl xz-utils python3-pip && \
    ln -sf python3 /usr/bin/python &&\
    python -m pip install awscli &&\
    mkdir -p "/opt/app"
RUN set -ex; \
    curl -SL https://github.com/twosigma/fastfreeze/releases/download/v1.3.0/fastfreeze-v1.3.0.tar.xz | \
    tar xJf - -C /opt; \
    ln -s /opt/fastfreeze/fastfreeze /usr/local/bin; \
    fastfreeze install
WORKDIR /opt/app
COPY main.py .
#RUN chmod 744 main.py
#USER 1001
ENTRYPOINT ["/bin/sh", "-c"]
CMD ["fastfreeze run -- python -u ./main.py"]
